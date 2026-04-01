package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleStatisticsResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "gamificationTransactionManager")
public class TitleAdminService {

    private final TitleRepository titleRepository;
    private final AchievementRepository achievementRepository;

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<TitleAdminResponse> getAllTitles() {
        return titleRepository.findAll().stream()
            .map(TitleAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public TitleAdminPageResponse searchTitles(String keyword, TitlePosition positionType, Pageable pageable) {
        Page<TitleAdminResponse> page;
        if (positionType == null) {
            page = titleRepository.searchByKeyword(keyword, pageable)
                .map(this::toResponseWithLinkedAchievement);
        } else {
            page = titleRepository.searchByKeywordAndPosition(keyword, positionType, pageable)
                .map(this::toResponseWithLinkedAchievement);
        }
        return TitleAdminPageResponse.from(page);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<TitleAdminResponse> getActiveTitles() {
        return titleRepository.findByIsActiveTrueOrderByIdAsc().stream()
            .map(TitleAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public TitleAdminResponse getTitle(Long id) {
        Title title = titleRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.title.not_found"));
        return toResponseWithLinkedAchievement(title);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<TitleAdminResponse> getTitlesByRarity(TitleRarity rarity) {
        return titleRepository.findByRarity(rarity).stream()
            .map(TitleAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<TitleAdminResponse> getTitlesByPosition(TitlePosition positionType) {
        return titleRepository.findByPositionTypeAndIsActiveTrueOrderByRarityAscIdAsc(positionType).stream()
            .map(TitleAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public TitleStatisticsResponse getStatistics() {
        Long totalCount = titleRepository.count();
        Long activeCount = titleRepository.countActiveTitles();
        Long leftCount = titleRepository.countByPositionTypeAndActive(TitlePosition.LEFT);
        Long rightCount = titleRepository.countByPositionTypeAndActive(TitlePosition.RIGHT);
        return new TitleStatisticsResponse(totalCount, activeCount, leftCount, rightCount);
    }

    public TitleAdminResponse createTitle(TitleAdminRequest request) {
        if (titleRepository.existsByName(request.getName())) {
            throw new CustomException("400", "error.title.duplicate_name");
        }

        Title title = Title.builder()
            .name(request.getName())
            .nameEn(request.getNameEn())
            .nameAr(request.getNameAr())
            .nameJa(request.getNameJa())
            .description(request.getDescription())
            .rarity(request.getRarity())
            .positionType(request.getPositionType())
            .colorCode(request.getColorCode())
            .iconUrl(request.getIconUrl())
            .acquisitionType(request.getAcquisitionType())
            .acquisitionCondition(request.getAcquisitionCondition())
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .build();

        Title saved = titleRepository.save(title);
        log.info("칭호 생성: id={}, name={}, position={}", saved.getId(), saved.getName(), saved.getPositionType());
        return TitleAdminResponse.from(saved);
    }

    public TitleAdminResponse updateTitle(Long id, TitleAdminRequest request) {
        Title title = titleRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.title.not_found"));

        if (!title.getName().equals(request.getName())
            && titleRepository.existsByName(request.getName())) {
            throw new CustomException("400", "error.title.duplicate_name");
        }

        title.setName(request.getName());
        title.setNameEn(request.getNameEn());
        title.setNameAr(request.getNameAr());
        title.setNameJa(request.getNameJa());
        title.setDescription(request.getDescription());
        title.setRarity(request.getRarity());
        title.setPositionType(request.getPositionType());
        title.setColorCode(request.getColorCode());
        title.setIconUrl(request.getIconUrl());
        title.setAcquisitionType(request.getAcquisitionType());
        title.setAcquisitionCondition(request.getAcquisitionCondition());
        title.setIsActive(request.getIsActive());

        Title saved = titleRepository.save(title);
        log.info("칭호 수정: id={}, name={}, position={}", id, saved.getName(), saved.getPositionType());
        return TitleAdminResponse.from(saved);
    }

    public TitleAdminResponse toggleActiveStatus(Long id) {
        Title title = titleRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.title.not_found"));

        title.setIsActive(!title.getIsActive());
        Title saved = titleRepository.save(title);
        log.info("칭호 활성 상태 변경: id={}, isActive={}", id, saved.getIsActive());
        return TitleAdminResponse.from(saved);
    }

    public void deleteTitle(Long id) {
        if (!titleRepository.existsById(id)) {
            throw new CustomException("404", "error.title.not_found");
        }
        titleRepository.deleteById(id);
        log.info("칭호 삭제: id={}", id);
    }

    private TitleAdminResponse toResponseWithLinkedAchievement(Title title) {
        List<Achievement> linkedAchievements = achievementRepository.findByRewardTitleId(title.getId());
        if (!linkedAchievements.isEmpty()) {
            Achievement achievement = linkedAchievements.get(0);
            return TitleAdminResponse.from(title, achievement.getId(), achievement.getName());
        }
        return TitleAdminResponse.from(title);
    }
}
