package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import static io.pinkspider.leveluptogethermvp.metaservice.domain.entity.MissionCategory.DEFAULT_CATEGORY_NAME;

import io.pinkspider.global.event.AchievementCompletedEvent;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.dto.GuildMembershipInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.UserAchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy.AchievementCheckStrategy;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy.AchievementCheckStrategyRegistry;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy.AchievementSyncContext;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserCategoryExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserAchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserCategoryExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsService;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.global.enums.ExpSourceType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import static io.pinkspider.global.config.AsyncConfig.EVENT_EXECUTOR;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final UserStatsRepository userStatsRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final UserCategoryExperienceRepository userCategoryExperienceRepository;
    private final GuildQueryFacade guildQueryFacade;
    private final MissionCategoryService missionCategoryService;
    private final TitleRepository titleRepository;

    // 업적 목록 조회 (캐시 사용)
    public List<AchievementResponse> getAllAchievements() {
        return getAllAchievements(null);
    }

    public List<AchievementResponse> getAllAchievements(String locale) {
        List<MissionCategoryResponse> activeCategories = missionCategoryService.getActiveCategories();
        Set<Long> activeCategoryIds = toCategoryIdSet(activeCategories);
        Map<Long, String> categoryNamesById = toCategoryNameMap(activeCategories, locale);
        return achievementCacheService.getVisibleAchievements().stream()
            .filter(a -> !isOrphanedCategoryAchievement(a, activeCategoryIds))
            .map(a -> AchievementResponse.from(a, categoryNamesById, locale))
            .toList();
    }

    public List<AchievementResponse> getAchievementsByCategoryCode(String categoryCode) {
        return getAchievementsByCategoryCode(categoryCode, null);
    }

    public List<AchievementResponse> getAchievementsByCategoryCode(String categoryCode, String locale) {
        List<MissionCategoryResponse> activeCategories = missionCategoryService.getActiveCategories();
        Set<Long> activeCategoryIds = toCategoryIdSet(activeCategories);
        Map<Long, String> categoryNamesById = toCategoryNameMap(activeCategories, locale);
        return achievementCacheService.getAchievementsByCategoryCode(categoryCode).stream()
            .filter(a -> !a.getIsHidden())
            .filter(a -> !isOrphanedCategoryAchievement(a, activeCategoryIds))
            .map(a -> AchievementResponse.from(a, categoryNamesById, locale))
            .toList();
    }

    public List<AchievementResponse> getAchievementsByMissionCategoryId(Long missionCategoryId) {
        return getAchievementsByMissionCategoryId(missionCategoryId, null);
    }

    public List<AchievementResponse> getAchievementsByMissionCategoryId(Long missionCategoryId, String locale) {
        List<MissionCategoryResponse> activeCategories = missionCategoryService.getActiveCategories();
        Set<Long> activeCategoryIds = toCategoryIdSet(activeCategories);
        Map<Long, String> categoryNamesById = toCategoryNameMap(activeCategories, locale);
        return achievementCacheService.getAchievementsByMissionCategoryId(missionCategoryId).stream()
            .filter(a -> !a.getIsHidden())
            .filter(a -> !isOrphanedCategoryAchievement(a, activeCategoryIds))
            .map(a -> AchievementResponse.from(a, categoryNamesById, locale))
            .toList();
    }

    // 유저의 업적 목록 조회
    public List<UserAchievementResponse> getUserAchievements(String userId) {
        return getUserAchievements(userId, null);
    }

    public List<UserAchievementResponse> getUserAchievements(String userId, String locale) {
        return buildUserAchievementResponses(
            userAchievementRepository.findByUserIdWithAchievement(userId), locale);
    }

    public List<UserAchievementResponse> getCompletedAchievements(String userId) {
        return getCompletedAchievements(userId, null);
    }

    public List<UserAchievementResponse> getCompletedAchievements(String userId, String locale) {
        return buildUserAchievementResponses(
            userAchievementRepository.findCompletedByUserId(userId), locale);
    }

    public List<UserAchievementResponse> getInProgressAchievements(String userId) {
        return getInProgressAchievements(userId, null);
    }

    public List<UserAchievementResponse> getInProgressAchievements(String userId, String locale) {
        return buildUserAchievementResponses(
            userAchievementRepository.findInProgressByUserId(userId), locale);
    }

    public List<UserAchievementResponse> getClaimableAchievements(String userId) {
        return getClaimableAchievements(userId, null);
    }

    public List<UserAchievementResponse> getClaimableAchievements(String userId, String locale) {
        return buildUserAchievementResponses(
            userAchievementRepository.findClaimableByUserId(userId), locale);
    }

    /**
     * QA-159: 마이페이지 업적 응답 빌더 — 메타 카테고리 이름 + 보상 칭호(name/rarity) 를 일괄 enrich.
     * QA-145 안전망(orphaned mission_category)도 함께 적용. LUT-255: locale 반영.
     */
    private List<UserAchievementResponse> buildUserAchievementResponses(
        List<UserAchievement> userAchievements, String locale) {
        List<MissionCategoryResponse> activeCategories = missionCategoryService.getActiveCategories();
        Set<Long> activeCategoryIds = toCategoryIdSet(activeCategories);
        Map<Long, String> categoryNamesById = toCategoryNameMap(activeCategories, locale);

        List<UserAchievement> filtered = userAchievements.stream()
            .filter(ua -> !isOrphanedCategoryAchievement(ua.getAchievement(), activeCategoryIds))
            .toList();

        Set<Long> titleIds = filtered.stream()
            .map(ua -> ua.getAchievement().getRewardTitleId())
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());

        Map<Long, Title> titlesById = titleIds.isEmpty()
            ? Collections.emptyMap()
            : titleRepository.findAllById(titleIds).stream()
                .collect(Collectors.toMap(Title::getId, t -> t));

        return filtered.stream()
            .map(ua -> UserAchievementResponse.from(ua, categoryNamesById, titlesById, locale))
            .toList();
    }

    /**
     * QA-145 안전장치: USER_CATEGORY_EXPERIENCE 데이터소스인데 mission_category_id 가 메타에 없으면
     * (카테고리 삭제 후 운영자가 정리 전인 상태) 응답에서 제외한다. 어드민 응답은 별도 경로라 영향 없음.
     */
    private boolean isOrphanedCategoryAchievement(Achievement achievement, Set<Long> activeCategoryIds) {
        if (achievement == null) {
            return false;
        }
        if (!"USER_CATEGORY_EXPERIENCE".equals(achievement.getCheckLogicDataSource())) {
            return false;
        }
        Long catId = achievement.getMissionCategoryId();
        if (catId == null) {
            return false;
        }
        return !activeCategoryIds.contains(catId);
    }

    private Set<Long> toCategoryIdSet(List<MissionCategoryResponse> categories) {
        return categories.stream()
            .map(MissionCategoryResponse::getId)
            .collect(Collectors.toSet());
    }

    private Map<Long, String> toCategoryNameMap(List<MissionCategoryResponse> categories, String locale) {
        return categories.stream()
            .collect(Collectors.toMap(MissionCategoryResponse::getId,
                c -> c.getLocalizedName(locale), (a, b) -> a));
    }

    // 업적 보상 수령
    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserAchievementResponse claimReward(String userId, Long achievementId) {
        UserAchievement userAchievement = userAchievementRepository
            .findByUserIdAndAchievementId(userId, achievementId)
            .orElseThrow(() -> new IllegalArgumentException("업적을 찾을 수 없습니다."));

        if (!Boolean.TRUE.equals(userAchievement.getIsCompleted())) {
            throw new IllegalStateException("업적을 완료하지 않았습니다.");
        }

        if (!claimRewardInternal(userId, userAchievement)) {
            throw new IllegalStateException("이미 보상을 수령했습니다.");
        }

        log.info("업적 보상 수령: userId={}, achievement={}", userId, userAchievement.getAchievement().getName());
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
                if (claimRewardInternal(userId, ua)) {
                    log.info("업적 보상 자동 수령 완료: userId={}, achievement={}", userId, ua.getAchievement().getName());
                }
            } catch (Exception e) {
                log.error("업적 보상 자동 수령 실패: userId={}, achievement={}, error={}",
                    userId, ua.getAchievement().getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 내부용 보상 수령 메서드 (이미 조회된 UserAchievement 사용)
     *
     * @return 이 호출이 실제로 보상을 수령했으면 true.
     *     이벤트 즉시 수령 / 홈 sync / 수동 claim 이 동시에 겹쳐도
     *     markRewardClaimed 원자적 UPDATE 가드로 한 경로만 지급한다.
     */
    private boolean claimRewardInternal(String userId, UserAchievement userAchievement) {
        int claimed = userAchievementRepository.markRewardClaimed(userAchievement.getId());
        if (claimed == 0) {
            log.info("업적 보상 이미 수령됨 (중복 수령 방지): userId={}, userAchievementId={}",
                userId, userAchievement.getId());
            return false;
        }

        // 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로 in-memory 상태를 동기화한다
        userAchievement.claimReward();

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
        return true;
    }

    // =============================================
    // 동적 업적 체크 메서드 (Strategy 패턴 기반)
    // =============================================

    /**
     * 특정 데이터 소스의 모든 활성 업적을 체크합니다.
     *
     * QA-178: 부모 트랜잭션(예: AttendanceService.checkIn) 안에서 호출되는 경우,
     * 업적 체크 strategy 중 하나라도 예외를 던지면 같은 트랜잭션이 rollback-only 로 마킹되어
     * 부모의 try-catch 가 예외를 삼켜도 commit 시 UnexpectedRollbackException 으로 전체가 롤백된다.
     * REQUIRES_NEW 로 분리해서 업적 체크 실패가 호출자 비즈니스 로직을 깨뜨리지 않도록 한다.
     *
     * @param userId 사용자 ID
     * @param dataSource 데이터 소스 (USER_STATS, USER_EXPERIENCE, FRIEND_SERVICE, GUILD_SERVICE, FEED_SERVICE)
     */
    @Transactional(
            transactionManager = "gamificationTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
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
     *
     * QA-116 최적화: source 데이터를 1회 사전 로드하여 전달 (AchievementSyncContext).
     *   기존: 316 업적 × Strategy 호출당 2 DB 쿼리 + per-achievement findByUserIdAndAchievementId.
     *   개선: UserStats / UserExperience / UserCategoryExperience / UserAchievement 각 1쿼리 일괄 로드.
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void checkAllDynamicAchievements(String userId) {
        AchievementSyncContext ctx = buildSyncContext(userId);
        List<Achievement> achievements = achievementCacheService.getAchievementsWithCheckLogic();

        for (Achievement achievement : achievements) {
            String dataSource = achievement.getCheckLogicDataSource();
            AchievementCheckStrategy strategy = strategyRegistry.getStrategy(dataSource);

            if (strategy != null) {
                checkAndUpdateAchievementWithContext(ctx, achievement, strategy);
            }
        }
    }

    /**
     * sync 1회분 source 데이터를 사전 로드한다 (~5쿼리).
     */
    private AchievementSyncContext buildSyncContext(String userId) {
        UserStats userStats = userStatsRepository.findByUserId(userId).orElse(null);
        UserExperience userExperience = userExperienceRepository.findByUserId(userId).orElse(null);
        List<UserCategoryExperience> categoryExperiences =
            userCategoryExperienceRepository.findByUserIdOrderByTotalExpDesc(userId);
        List<UserAchievement> userAchievements = userAchievementRepository.findAllByUserIdForSync(userId);

        boolean guildMaster = false;
        try {
            List<GuildMembershipInfo> memberships = guildQueryFacade.getUserGuildMemberships(userId);
            if (memberships != null) {
                guildMaster = memberships.stream().anyMatch(GuildMembershipInfo::isMaster);
            }
        } catch (Exception e) {
            log.warn("길드 마스터 여부 조회 실패: userId={}, error={}", userId, e.getMessage());
        }

        return new AchievementSyncContext(
            userId,
            userStats,
            userExperience,
            categoryExperiences != null ? categoryExperiences : Collections.emptyList(),
            guildMaster,
            userAchievements != null ? userAchievements : Collections.emptyList()
        );
    }

    /**
     * 사전 로드된 컨텍스트를 사용해 단일 업적의 진행도/완료 여부를 갱신한다.
     * 핵심 차이점: DB 조회 대신 ctx 로 in-memory 평가, getOrCreateUserAchievement 도 ctx 캐시 우선.
     */
    private void checkAndUpdateAchievementWithContext(
        AchievementSyncContext ctx,
        Achievement achievement,
        AchievementCheckStrategy strategy
    ) {
        if (!Boolean.TRUE.equals(achievement.getIsActive())) {
            return;
        }

        UserAchievement existing = ctx.findUserAchievement(achievement.getId());
        boolean alreadyCompleted = existing != null && Boolean.TRUE.equals(existing.getIsCompleted());

        // 이미 완료된 행: currentCount 만 stale 보정 (보상 재수령 금지)
        if (alreadyCompleted) {
            Object currentValue = strategy.fetchCurrentValue(ctx, achievement);
            if (currentValue instanceof Number n) {
                int latest = n.intValue();
                if (existing.getCurrentCount() == null || existing.getCurrentCount() != latest) {
                    existing.setCount(latest); // 변경된 경우만 dirty 처리
                }
            }
            return;
        }

        boolean conditionMet = strategy.checkCondition(ctx, achievement);

        if (conditionMet) {
            UserAchievement userAchievement = existing != null
                ? existing
                : getOrCreateUserAchievementWithCtx(ctx, achievement);

            Object currentValue = strategy.fetchCurrentValue(ctx, achievement);
            int newCount;
            if (currentValue instanceof Number n) {
                newCount = n.intValue();
            } else if (currentValue instanceof Boolean b && Boolean.TRUE.equals(b)) {
                newCount = 1;
            } else {
                return;
            }

            // no-op skip: 변경 없으면 setCount 호출 안 함 (불필요한 dirty 표시 방지)
            if (userAchievement.getCurrentCount() == null || userAchievement.getCurrentCount() != newCount) {
                userAchievement.setCount(newCount);
            }

            if (Boolean.TRUE.equals(userAchievement.getIsCompleted())) {
                userStatsService.recordAchievementCompleted(ctx.getUserId());
                log.info("동적 업적 달성! userId={}, achievement={}", ctx.getUserId(), achievement.getName());
                if (!Boolean.TRUE.equals(achievement.getIsHidden())) {
                    eventPublisher.publishEvent(new AchievementCompletedEvent(
                        ctx.getUserId(),
                        achievement.getId(),
                        achievement.getName()
                    ));
                }
            }
        } else {
            Object currentValue = strategy.fetchCurrentValue(ctx, achievement);
            if (currentValue instanceof Number n) {
                int newCount = n.intValue();
                UserAchievement userAchievement = existing != null
                    ? existing
                    : getOrCreateUserAchievementWithCtx(ctx, achievement);
                if (userAchievement.getCurrentCount() == null || userAchievement.getCurrentCount() != newCount) {
                    userAchievement.setCount(newCount);
                }
            }
        }
    }

    /**
     * 컨텍스트에 캐시되어 있지 않은 경우만 INSERT, 캐시도 갱신.
     */
    private UserAchievement getOrCreateUserAchievementWithCtx(AchievementSyncContext ctx, Achievement achievement) {
        UserAchievement created = getOrCreateUserAchievement(ctx.getUserId(), achievement);
        ctx.registerUserAchievement(created);
        return created;
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
                // 완료 즉시 보상 자동 수령 — 홈 진입 sync 시점까지 지연되던 것을 제거.
                // 실패해도 완료 처리는 유지되고 다음 홈 sync 의 autoClaimRewards 가 재시도한다.
                try {
                    claimRewardInternal(userId, userAchievement);
                } catch (Exception e) {
                    log.warn("업적 보상 즉시 수령 실패 (홈 sync 에서 재시도): userId={}, achievement={}, error={}",
                        userId, achievement.getName(), e.getMessage());
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
