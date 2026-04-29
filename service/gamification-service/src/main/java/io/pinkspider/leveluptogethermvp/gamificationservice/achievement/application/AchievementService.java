package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import static io.pinkspider.leveluptogethermvp.metaservice.domain.entity.MissionCategory.DEFAULT_CATEGORY_NAME;

import io.pinkspider.global.event.AchievementCompletedEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.UserAchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy.AchievementCheckStrategy;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy.AchievementCheckStrategyRegistry;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserAchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsService;
import io.pinkspider.global.enums.ExpSourceType;
import java.util.List;
import java.util.Optional;
import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserStatsService userStatsService;
    private final UserExperienceService userExperienceService;
    private final TitleService titleService;
    private final ApplicationEventPublisher eventPublisher;
    private final AchievementCheckStrategyRegistry strategyRegistry;
    private final AchievementCacheService achievementCacheService;

    // 업적 목록 조회 (캐시 사용)
    public List<AchievementResponse> getAllAchievements() {
        return achievementCacheService.getVisibleAchievements().stream()
            .map(AchievementResponse::from)
            .toList();
    }

    public List<AchievementResponse> getAchievementsByCategoryCode(String categoryCode) {
        return achievementCacheService.getAchievementsByCategoryCode(categoryCode).stream()
            .filter(a -> !a.getIsHidden())
            .map(AchievementResponse::from)
            .toList();
    }

    public List<AchievementResponse> getAchievementsByMissionCategoryId(Long missionCategoryId) {
        return achievementCacheService.getAchievementsByMissionCategoryId(missionCategoryId).stream()
            .filter(a -> !a.getIsHidden())
            .map(AchievementResponse::from)
            .toList();
    }

    // 유저의 업적 목록 조회
    public List<UserAchievementResponse> getUserAchievements(String userId) {
        return userAchievementRepository.findByUserIdWithAchievement(userId).stream()
            .map(UserAchievementResponse::from)
            .toList();
    }

    public List<UserAchievementResponse> getCompletedAchievements(String userId) {
        return userAchievementRepository.findCompletedByUserId(userId).stream()
            .map(UserAchievementResponse::from)
            .toList();
    }

    public List<UserAchievementResponse> getInProgressAchievements(String userId) {
        return userAchievementRepository.findInProgressByUserId(userId).stream()
            .map(UserAchievementResponse::from)
            .toList();
    }

    public List<UserAchievementResponse> getClaimableAchievements(String userId) {
        return userAchievementRepository.findClaimableByUserId(userId).stream()
            .map(UserAchievementResponse::from)
            .toList();
    }

    // 업적 보상 수령
    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserAchievementResponse claimReward(String userId, Long achievementId) {
        UserAchievement userAchievement = userAchievementRepository
            .findByUserIdAndAchievementId(userId, achievementId)
            .orElseThrow(() -> new IllegalArgumentException("업적을 찾을 수 없습니다."));

        userAchievement.claimReward();

        Achievement achievement = userAchievement.getAchievement();

        // 경험치 보상
        if (achievement.getRewardExp() > 0) {
            userExperienceService.addExperience(
                userId,
                achievement.getRewardExp(),
                ExpSourceType.ACHIEVEMENT,
                achievement.getId(),
                "업적 달성 보상: " + achievement.getName(),
                DEFAULT_CATEGORY_NAME
            );
        }

        // 칭호 보상
        if (achievement.getRewardTitleId() != null) {
            titleService.grantTitle(userId, achievement.getRewardTitleId());
        }

        log.info("업적 보상 수령: userId={}, achievement={}", userId, achievement.getName());
        return UserAchievementResponse.from(userAchievement);
    }

    // =============================================
    // 업적 동기화 (홈 접근 시 호출)
    // =============================================

    /**
     * 유저의 모든 업적을 체크하고 완료된 업적의 보상을 자동으로 수령합니다.
     * 홈 화면 접근 시 호출되어 기존 유저들의 업적을 소급 적용합니다.
     * @param userId 사용자 ID
     * @return 동기화 성공 여부 (false 면 부분 또는 전체 실패)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public boolean syncUserAchievements(String userId) {
        log.info("업적 동기화 시작: userId={}", userId);

        try {
            // 동적 Strategy 패턴을 사용하여 모든 업적 체크
            checkAllDynamicAchievements(userId);

            // 완료되었지만 보상을 받지 않은 업적 자동 수령
            autoClaimRewards(userId);

            log.info("업적 동기화 완료: userId={}", userId);
            return true;
        } catch (Exception e) {
            log.error("업적 동기화 실패: userId={}, error={}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 완료되었지만 보상을 받지 않은 업적의 보상을 자동으로 수령합니다.
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void autoClaimRewards(String userId) {
        List<UserAchievement> claimableAchievements = userAchievementRepository.findClaimableByUserId(userId);
        log.info("보상 수령 가능한 업적 조회: userId={}, count={}", userId, claimableAchievements.size());

        for (UserAchievement ua : claimableAchievements) {
            try {
                log.info("업적 보상 수령 시도: userId={}, achievementId={}, achievementName={}",
                    userId, ua.getAchievement().getId(), ua.getAchievement().getName());
                claimRewardInternal(userId, ua);
                log.info("업적 보상 자동 수령 완료: userId={}, achievement={}", userId, ua.getAchievement().getName());
            } catch (Exception e) {
                log.error("업적 보상 자동 수령 실패: userId={}, achievement={}, error={}",
                    userId, ua.getAchievement().getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 내부용 보상 수령 메서드 (이미 조회된 UserAchievement 사용)
     */
    private void claimRewardInternal(String userId, UserAchievement userAchievement) {
        userAchievement.claimReward();
        log.info("claimReward() 호출 완료: userId={}, isRewardClaimed={}",
            userId, userAchievement.getIsRewardClaimed());

        Achievement achievement = userAchievement.getAchievement();

        // 경험치 보상
        if (achievement.getRewardExp() > 0) {
            log.info("경험치 보상 지급 시작: userId={}, exp={}", userId, achievement.getRewardExp());
            userExperienceService.addExperience(
                userId,
                achievement.getRewardExp(),
                ExpSourceType.ACHIEVEMENT,
                achievement.getId(),
                "업적 달성 보상: " + achievement.getName(),
                DEFAULT_CATEGORY_NAME
            );
            log.info("경험치 보상 지급 완료: userId={}", userId);
        }

        // 칭호 보상
        if (achievement.getRewardTitleId() != null) {
            log.info("칭호 보상 지급 시작: userId={}, titleId={}", userId, achievement.getRewardTitleId());
            titleService.grantTitle(userId, achievement.getRewardTitleId());
            log.info("칭호 보상 지급 완료: userId={}, titleId={}", userId, achievement.getRewardTitleId());
        }

        // 명시적으로 저장하여 변경사항 반영
        userAchievementRepository.save(userAchievement);
        log.info("업적 보상 수령 DB 저장 완료: userId={}, achievement={}", userId, achievement.getName());
    }

    // =============================================
    // 동적 업적 체크 메서드 (Strategy 패턴 기반)
    // =============================================

    /**
     * 특정 데이터 소스의 모든 활성 업적을 체크합니다.
     * @param userId 사용자 ID
     * @param dataSource 데이터 소스 (USER_STATS, USER_EXPERIENCE, FRIEND_SERVICE, GUILD_SERVICE, FEED_SERVICE)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void checkAchievementsByDataSource(String userId, String dataSource) {
        AchievementCheckStrategy strategy = strategyRegistry.getStrategy(dataSource);
        if (strategy == null) {
            log.warn("알 수 없는 데이터 소스입니다: {}", dataSource);
            return;
        }

        // 캐시 사용
        List<Achievement> achievements = achievementCacheService.getAchievementsByDataSource(dataSource);

        for (Achievement achievement : achievements) {
            checkAndUpdateAchievementDynamic(userId, achievement, strategy);
        }
    }

    /**
     * 체크 로직이 설정된 모든 활성 업적을 체크합니다.
     * 이벤트 발생 시 전체 업적을 체크하는 용도로 사용합니다.
     * @param userId 사용자 ID
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void checkAllDynamicAchievements(String userId) {
        // 캐시 사용
        List<Achievement> achievements = achievementCacheService.getAchievementsWithCheckLogic();

        for (Achievement achievement : achievements) {
            String dataSource = achievement.getCheckLogicDataSource();
            AchievementCheckStrategy strategy = strategyRegistry.getStrategy(dataSource);

            if (strategy != null) {
                checkAndUpdateAchievementDynamic(userId, achievement, strategy);
            }
        }
    }

    /**
     * Strategy를 사용하여 동적으로 업적 조건을 체크하고 진행도를 업데이트합니다.
     *
     * QA-113 / B10:
     *   기존 구현은 isCompleted=true 인 경우 곧바로 return 하여 currentCount가 stale 상태로 남았다.
     *   현재 구현은 isCompleted=true 인 경우에도 currentCount 만 최신 값으로 갱신해 stale 표시를 막는다.
     *   완료 트리거(이벤트 + recordAchievementCompleted) 는 신규 완료 시 1회만 발생하며,
     *   이미 보상을 받은(isRewardClaimed=true) 행은 절대 재수령되지 않는다.
     */
    private void checkAndUpdateAchievementDynamic(String userId, Achievement achievement, AchievementCheckStrategy strategy) {
        if (!achievement.getIsActive()) {
            return;
        }

        Optional<UserAchievement> existingOpt = userAchievementRepository.findByUserIdAndAchievementId(userId, achievement.getId());
        boolean alreadyCompleted = existingOpt.isPresent() && Boolean.TRUE.equals(existingOpt.get().getIsCompleted());

        // 이미 완료된 행은 currentCount 만 stale 보정하고 종료 (보상 재수령 금지)
        if (alreadyCompleted) {
            Object currentValue = strategy.fetchCurrentValue(userId, achievement);
            if (currentValue instanceof Number n) {
                UserAchievement existing = existingOpt.get();
                int latest = n.intValue();
                if (existing.getCurrentCount() == null || existing.getCurrentCount() != latest) {
                    existing.setCount(latest); // setCount 는 isCompleted=true 인 경우 재트리거하지 않음
                }
            }
            return;
        }

        // 미완료 행: Strategy 기반 조건 체크 + 진행도 갱신
        boolean conditionMet = strategy.checkCondition(userId, achievement);

        if (conditionMet) {
            UserAchievement userAchievement = existingOpt.orElseGet(() -> getOrCreateUserAchievement(userId, achievement));

            Object currentValue = strategy.fetchCurrentValue(userId, achievement);
            if (currentValue instanceof Number n) {
                userAchievement.setCount(n.intValue());
            } else if (currentValue instanceof Boolean b && Boolean.TRUE.equals(b)) {
                userAchievement.setCount(1);
            }

            if (Boolean.TRUE.equals(userAchievement.getIsCompleted())) {
                userStatsService.recordAchievementCompleted(userId);
                log.info("동적 업적 달성! userId={}, achievement={}", userId, achievement.getName());
                if (!Boolean.TRUE.equals(achievement.getIsHidden())) {
                    eventPublisher.publishEvent(new AchievementCompletedEvent(
                        userId,
                        achievement.getId(),
                        achievement.getName()
                    ));
                }
            }
        } else {
            Object currentValue = strategy.fetchCurrentValue(userId, achievement);
            if (currentValue instanceof Number n) {
                UserAchievement userAchievement = existingOpt.orElseGet(() -> getOrCreateUserAchievement(userId, achievement));
                userAchievement.setCount(n.intValue());
            }
        }
    }

    /**
     * UserAchievement 생성 또는 조회 (레이스 컨디션 안전)
     * 동시 요청으로 중복 키 예외 발생 시 기존 레코드를 조회하여 반환
     */
    private UserAchievement getOrCreateUserAchievement(String userId, Achievement achievement) {
        // 먼저 기존 레코드 조회
        return userAchievementRepository.findByUserIdAndAchievementId(userId, achievement.getId())
            .orElseGet(() -> {
                try {
                    UserAchievement newAchievement = UserAchievement.builder()
                        .userId(userId)
                        .achievement(achievement)
                        .currentCount(0)
                        .build();
                    // saveAndFlush로 즉시 INSERT 실행 및 예외 발생
                    return userAchievementRepository.saveAndFlush(newAchievement);
                } catch (DataIntegrityViolationException e) {
                    // 레이스 컨디션: 다른 스레드가 먼저 저장한 경우, 기존 레코드 조회
                    log.debug("UserAchievement 중복 감지, 기존 레코드 조회: userId={}, achievementId={}", userId, achievement.getId());
                    return userAchievementRepository.findByUserIdAndAchievementId(userId, achievement.getId())
                        .orElseThrow(() -> new IllegalStateException(
                            "UserAchievement not found after duplicate key error: userId=" + userId + ", achievementId=" + achievement.getId()));
                }
            });
    }
}
