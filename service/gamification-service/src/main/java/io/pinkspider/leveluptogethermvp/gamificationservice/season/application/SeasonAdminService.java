package io.pinkspider.leveluptogethermvp.gamificationservice.season.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure.SeasonRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "gamificationTransactionManager")
public class SeasonAdminService {

    private final SeasonRepository seasonRepository;
    private final RedisTemplate<String, Object> redisTemplateForObject;

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<SeasonAdminResponse> getAllSeasons() {
        return seasonRepository.findAllByOrderBySortOrderAscStartAtDesc().stream()
            .map(SeasonAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public SeasonAdminPageResponse searchSeasons(String keyword, Pageable pageable) {
        Page<SeasonAdminResponse> page;
        if (keyword != null && !keyword.isBlank()) {
            page = seasonRepository.searchByKeyword(keyword, pageable)
                .map(SeasonAdminResponse::from);
        } else {
            page = seasonRepository.findAllByOrderBySortOrderAscStartAtDesc(pageable)
                .map(SeasonAdminResponse::from);
        }
        return SeasonAdminPageResponse.from(page);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public SeasonAdminResponse getSeason(Long id) {
        Season season = seasonRepository.findById(id)
            .orElseThrow(() -> new CustomException("120001", "error.season.not_found"));
        return SeasonAdminResponse.from(season);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public SeasonAdminResponse getCurrentSeason() {
        return seasonRepository.findCurrentSeason(LocalDateTime.now())
            .map(SeasonAdminResponse::from)
            .orElse(null);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<SeasonAdminResponse> getUpcomingSeasons() {
        return seasonRepository.findUpcomingSeasons(LocalDateTime.now()).stream()
            .map(SeasonAdminResponse::from)
            .collect(Collectors.toList());
    }

    public SeasonAdminResponse createSeason(SeasonAdminRequest request) {
        validateSeasonDates(request.startAt(), request.endAt());

        boolean isActive = request.isActive() != null ? request.isActive() : true;
        if (isActive) {
            validateNoOverlappingActiveSeason(request.startAt(), request.endAt(), null);
        }

        Season season = Season.builder()
            .title(request.title())
            .description(request.description())
            .startAt(request.startAt())
            .endAt(request.endAt())
            .isActive(isActive)
            .rewardTitleId(request.rewardTitleId())
            .rewardTitleName(request.rewardTitleName())
            .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
            .createdBy(request.createdBy())
            .modifiedBy(request.createdBy())
            .build();

        Season saved = seasonRepository.save(season);
        log.info("시즌 생성: {} (ID: {}) by {}", request.title(), saved.getId(), request.createdBy());

        evictAllSeasonCaches();
        return SeasonAdminResponse.from(saved);
    }

    public SeasonAdminResponse updateSeason(Long id, SeasonAdminRequest request) {
        Season season = seasonRepository.findById(id)
            .orElseThrow(() -> new CustomException("120001", "error.season.not_found"));

        validateSeasonDates(request.startAt(), request.endAt());

        boolean isActive = request.isActive() != null ? request.isActive() : season.getIsActive();
        if (isActive) {
            validateNoOverlappingActiveSeason(request.startAt(), request.endAt(), id);
        }

        season.setTitle(request.title());
        season.setDescription(request.description());
        season.setStartAt(request.startAt());
        season.setEndAt(request.endAt());
        season.setIsActive(isActive);
        season.setRewardTitleId(request.rewardTitleId());
        season.setRewardTitleName(request.rewardTitleName());
        if (request.sortOrder() != null) {
            season.setSortOrder(request.sortOrder());
        }
        season.setModifiedBy(request.modifiedBy());

        Season updated = seasonRepository.save(season);
        log.info("시즌 수정: {} (ID: {}) by {}", request.title(), id, request.modifiedBy());

        evictAllSeasonCaches();
        return SeasonAdminResponse.from(updated);
    }

    public void deleteSeason(Long id) {
        Season season = seasonRepository.findById(id)
            .orElseThrow(() -> new CustomException("120001", "error.season.not_found"));

        log.info("시즌 삭제: {} (ID: {})", season.getTitle(), id);
        seasonRepository.delete(season);

        evictAllSeasonCaches();
    }

    public SeasonAdminResponse toggleActive(Long id) {
        Season season = seasonRepository.findById(id)
            .orElseThrow(() -> new CustomException("120001", "error.season.not_found"));

        if (!season.getIsActive()) {
            validateNoOverlappingActiveSeason(season.getStartAt(), season.getEndAt(), id);
        }

        season.setIsActive(!season.getIsActive());
        Season updated = seasonRepository.save(season);
        log.info("시즌 상태 변경: {} (ID: {}) -> {}", season.getTitle(), id, season.getIsActive());

        evictAllSeasonCaches();
        return SeasonAdminResponse.from(updated);
    }

    private void validateSeasonDates(LocalDateTime startAt, LocalDateTime endAt) {
        if (endAt.isBefore(startAt) || endAt.isEqual(startAt)) {
            throw new CustomException("120002", "error.season.end_before_start");
        }
    }

    private void validateNoOverlappingActiveSeason(LocalDateTime startAt, LocalDateTime endAt, Long excludeId) {
        boolean hasOverlap;
        if (excludeId != null) {
            hasOverlap = seasonRepository.existsOverlappingActiveSeason(startAt, endAt, excludeId);
        } else {
            hasOverlap = seasonRepository.existsOverlappingActiveSeasonForNew(startAt, endAt);
        }

        if (hasOverlap) {
            throw new CustomException("120003", "error.season.overlap");
        }
    }

    private void evictAllSeasonCaches() {
        try {
            Set<String> keys1 = redisTemplateForObject.keys("currentSeason*");
            if (keys1 != null && !keys1.isEmpty()) {
                redisTemplateForObject.delete(keys1);
                log.debug("currentSeason 캐시 삭제: {} 개", keys1.size());
            }
            Set<String> keys2 = redisTemplateForObject.keys("seasonMvpData*");
            if (keys2 != null && !keys2.isEmpty()) {
                redisTemplateForObject.delete(keys2);
                log.debug("seasonMvpData 캐시 삭제: {} 개", keys2.size());
            }
        } catch (Exception e) {
            log.warn("시즌 캐시 삭제 실패", e);
        }
    }
}
