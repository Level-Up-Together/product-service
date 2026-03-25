package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AchievementCategory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.CheckLogicType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.entity.Event;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.infrastructure.EventRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementCategoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.CheckLogicTypeRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserAchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "gamificationTransactionManager")
public class AchievementAdminService {

    private final AchievementRepository achievementRepository;
    private final AchievementCategoryRepository achievementCategoryRepository;
    private final CheckLogicTypeRepository checkLogicTypeRepository;
    private final EventRepository eventRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserTitleRepository userTitleRepository;
    private final TitleRepository titleRepository;

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<AchievementAdminResponse> getAllAchievements() {
        return achievementRepository.findAll().stream()
            .map(this::toResponseWithEnrichment)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public AchievementAdminPageResponse searchAchievements(String keyword, Long categoryId, Pageable pageable) {
        Page<AchievementAdminResponse> page = achievementRepository
            .searchByKeywordAndCategoryId(keyword, categoryId, pageable)
            .map(this::toResponseWithEnrichment);
        return AchievementAdminPageResponse.from(page);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<AchievementAdminResponse> getActiveAchievements() {
        return achievementRepository.findByIsActiveTrueOrderByIdAsc().stream()
            .map(this::toResponseWithEnrichment)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<AchievementAdminResponse> getVisibleAchievements() {
        return achievementRepository.findVisibleAchievementsOrderByIdAsc().stream()
            .map(a -> AchievementAdminResponse.from(a))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public AchievementAdminResponse getAchievement(Long id) {
        Achievement achievement = achievementRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.achievement.not_found"));
        return toResponseWithEnrichment(achievement);
    }

    @Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
    public List<AchievementAdminResponse> getAchievementsByCategoryCode(String categoryCode) {
        return achievementRepository.findByCategoryCode(categoryCode).stream()
            .map(AchievementAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Caching(evict = {
        @CacheEvict(value = "achievements", allEntries = true)
    })
    public AchievementAdminResponse createAchievement(AchievementAdminRequest request) {
        AchievementCategory category = achievementCategoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new CustomException("404", "error.achievement.category.not_found"));

        CheckLogicType checkLogicType = checkLogicTypeRepository.findById(request.getCheckLogicTypeId())
            .orElseThrow(() -> new CustomException("404", "error.checklogic.not_found"));

        String eventName = null;
        if (request.getEventId() != null) {
            Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new CustomException("404", "error.event.not_found"));
            eventName = event.getName();
        }

        Achievement achievement = Achievement.builder()
            .name(request.getName())
            .nameEn(request.getNameEn())
            .nameAr(request.getNameAr())
            .description(request.getDescription())
            .descriptionEn(request.getDescriptionEn())
            .descriptionAr(request.getDescriptionAr())
            .iconUrl(request.getIconUrl())
            .requiredCount(request.getRequiredCount())
            .rewardExp(request.getRewardExp() != null ? request.getRewardExp() : 0)
            .rewardTitleId(request.getRewardTitleId())
            .missionCategoryId(request.getMissionCategoryId())
            .missionCategoryName(request.getMissionCategoryName())
            .checkLogicTypeId(request.getCheckLogicTypeId())
            .checkLogicDataSource(checkLogicType.getDataSource().getCode())
            .checkLogicDataField(checkLogicType.getDataField())
            .comparisonOperator(checkLogicType.getComparisonOperator().getCode())
            .isHidden(request.getIsHidden() != null ? request.getIsHidden() : false)
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .eventId(request.getEventId())
            .eventName(eventName)
            .build();

        achievement.setCategory(category);

        Achievement saved = achievementRepository.save(achievement);
        log.info("업적 생성 및 캐시 갱신: id={}, name={}", saved.getId(), saved.getName());
        return toResponseWithEnrichment(saved);
    }

    @Caching(evict = {
        @CacheEvict(value = "achievements", allEntries = true)
    })
    public AchievementAdminResponse updateAchievement(Long id, AchievementAdminRequest request) {
        Achievement achievement = achievementRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.achievement.not_found"));

        AchievementCategory category = achievementCategoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new CustomException("404", "error.achievement.category.not_found"));

        CheckLogicType checkLogicType = checkLogicTypeRepository.findById(request.getCheckLogicTypeId())
            .orElseThrow(() -> new CustomException("404", "error.checklogic.not_found"));

        String eventName = null;
        if (request.getEventId() != null) {
            Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new CustomException("404", "error.event.not_found"));
            eventName = event.getName();
        }

        Long oldTitleId = achievement.getRewardTitleId();
        Long newTitleId = request.getRewardTitleId();
        boolean titleChanged = newTitleId != null && !Objects.equals(oldTitleId, newTitleId);

        achievement.setName(request.getName());
        achievement.setNameEn(request.getNameEn());
        achievement.setNameAr(request.getNameAr());
        achievement.setDescription(request.getDescription());
        achievement.setDescriptionEn(request.getDescriptionEn());
        achievement.setDescriptionAr(request.getDescriptionAr());
        achievement.setCategory(category);
        achievement.setIconUrl(request.getIconUrl());
        achievement.setRequiredCount(request.getRequiredCount());
        achievement.setRewardExp(request.getRewardExp());
        achievement.setRewardTitleId(request.getRewardTitleId());
        achievement.setMissionCategoryId(request.getMissionCategoryId());
        achievement.setMissionCategoryName(request.getMissionCategoryName());
        achievement.setCheckLogicTypeId(request.getCheckLogicTypeId());
        achievement.setCheckLogicDataSource(checkLogicType.getDataSource().getCode());
        achievement.setCheckLogicDataField(checkLogicType.getDataField());
        achievement.setComparisonOperator(checkLogicType.getComparisonOperator().getCode());
        achievement.setIsHidden(request.getIsHidden());
        achievement.setIsActive(request.getIsActive());
        achievement.setEventId(request.getEventId());
        achievement.setEventName(eventName);

        Achievement saved = achievementRepository.save(achievement);
        log.info("업적 수정 및 캐시 갱신: id={}, name={}", id, saved.getName());

        if (titleChanged) {
            grantTitleToExistingAchievers(id, newTitleId);
        }

        return toResponseWithEnrichment(saved);
    }

    @Caching(evict = {
        @CacheEvict(value = "achievements", allEntries = true)
    })
    public AchievementAdminResponse toggleActiveStatus(Long id) {
        Achievement achievement = achievementRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.achievement.not_found"));

        achievement.setIsActive(!achievement.getIsActive());
        Achievement saved = achievementRepository.save(achievement);
        log.info("업적 활성 상태 변경 및 캐시 갱신: id={}, isActive={}", id, saved.getIsActive());
        return AchievementAdminResponse.from(saved);
    }

    @Caching(evict = {
        @CacheEvict(value = "achievements", allEntries = true)
    })
    public void deleteAchievement(Long id) {
        if (!achievementRepository.existsById(id)) {
            throw new CustomException("404", "error.achievement.not_found");
        }
        achievementRepository.deleteById(id);
        log.info("업적 삭제 및 캐시 갱신: id={}", id);
    }

    /**
     * 업적을 이미 달성하고 보상을 수령한 사용자들에게 새 칭호를 소급 부여
     */
    private void grantTitleToExistingAchievers(Long achievementId, Long titleId) {
        Title title = titleRepository.findById(titleId).orElse(null);
        if (title == null) {
            log.warn("소급 칭호 부여 실패: titleId={} 칭호를 찾을 수 없음", titleId);
            return;
        }

        List<UserAchievement> achievers = userAchievementRepository
            .findByAchievementIdAndIsCompletedTrueAndIsRewardClaimedTrue(achievementId);

        int grantedCount = 0;
        for (UserAchievement ua : achievers) {
            String userId = ua.getUserId();

            if (userTitleRepository.existsByUserIdAndTitleId(userId, titleId)) {
                continue;
            }

            UserTitle userTitle = UserTitle.builder()
                .userId(userId)
                .title(title)
                .acquiredAt(LocalDateTime.now())
                .isEquipped(false)
                .build();
            userTitleRepository.save(userTitle);
            grantedCount++;
        }

        log.info("기존 업적 달성자에게 칭호 소급 부여: achievementId={}, titleId={}, grantedCount={}",
            achievementId, titleId, grantedCount);
    }

    private AchievementAdminResponse toResponseWithEnrichment(Achievement achievement) {
        String rewardTitleName = null;
        String checkLogicTypeName = null;

        if (achievement.getRewardTitleId() != null) {
            Optional<Title> title = titleRepository.findById(achievement.getRewardTitleId());
            if (title.isPresent()) {
                rewardTitleName = title.get().getName();
            }
        }

        if (achievement.getCheckLogicTypeId() != null) {
            Optional<CheckLogicType> checkLogicType = checkLogicTypeRepository.findById(achievement.getCheckLogicTypeId());
            if (checkLogicType.isPresent()) {
                checkLogicTypeName = checkLogicType.get().getName();
            }
        }

        return AchievementAdminResponse.from(achievement, rewardTitleName, checkLogicTypeName);
    }
}
