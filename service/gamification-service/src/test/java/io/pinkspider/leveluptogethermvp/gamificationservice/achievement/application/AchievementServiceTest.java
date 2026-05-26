package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy.AchievementCheckStrategy;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy.AchievementCheckStrategyRegistry;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy.AchievementSyncContext;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserAchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserCategoryExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.AchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.UserAchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsService;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock
    private AchievementRepository achievementRepository;

    @Mock
    private UserAchievementRepository userAchievementRepository;

    @Mock
    private UserStatsService userStatsService;

    @Mock
    private UserExperienceService userExperienceService;

    @Mock
    private TitleService titleService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AchievementCheckStrategyRegistry strategyRegistry;

    @Mock
    private AchievementCheckStrategy mockStrategy;

    @Mock
    private AchievementCacheService achievementCacheService;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private UserExperienceRepository userExperienceRepository;

    @Mock
    private UserCategoryExperienceRepository userCategoryExperienceRepository;

    @Mock
    private GuildQueryFacade guildQueryFacade;

    @Mock
    private MissionCategoryService missionCategoryService;

    @InjectMocks
    private AchievementService achievementService;

    private static final String TEST_USER_ID = "test-user-123";

    private Achievement createTestAchievement(Long id, String name, int targetCount, int rewardExp) {
        Achievement achievement = Achievement.builder()
            .name(name)
            .description(name + " 설명")
            .categoryCode("MISSION")
            .requiredCount(targetCount)
            .rewardExp(rewardExp)
            .isActive(true)
            .isHidden(false)
            .checkLogicDataSource("USER_STATS")
            .checkLogicDataField("totalMissionCompletions")
            .comparisonOperator("GTE")
            .build();
        setId(achievement, id);
        return achievement;
    }

    private UserAchievement createTestUserAchievement(Long id, String userId, Achievement achievement, int currentCount, boolean isCompleted) {
        UserAchievement userAchievement = UserAchievement.builder()
            .userId(userId)
            .achievement(achievement)
            .currentCount(currentCount)
            .isCompleted(isCompleted)
            .isRewardClaimed(false)
            .build();
        setId(userAchievement, id);
        return userAchievement;
    }

    @Nested
    @DisplayName("getAllAchievements 테스트")
    class GetAllAchievementsTest {

        @Test
        @DisplayName("전체 업적 목록을 조회한다")
        void getAllAchievements_success() {
            // given
            Achievement achievement1 = createTestAchievement(1L, "FIRST_MISSION_COMPLETE", 1, 50);
            Achievement achievement2 = createTestAchievement(2L, "MISSION_COMPLETE_10", 10, 100);

            when(achievementCacheService.getVisibleAchievements()).thenReturn(List.of(achievement1, achievement2));

            // when
            List<AchievementResponse> result = achievementService.getAllAchievements();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("FIRST_MISSION_COMPLETE");
            assertThat(result.get(1).getName()).isEqualTo("MISSION_COMPLETE_10");
        }
    }

    @Nested
    @DisplayName("getAchievementsByCategoryCode 테스트")
    class GetAchievementsByCategoryCodeTest {

        @Test
        @DisplayName("카테고리별 업적을 조회한다")
        void getAchievementsByCategoryCode_success() {
            // given
            Achievement achievement = createTestAchievement(1L, "FIRST_MISSION_COMPLETE", 1, 50);

            when(achievementCacheService.getAchievementsByCategoryCode("MISSION"))
                .thenReturn(List.of(achievement));

            // when
            List<AchievementResponse> result = achievementService.getAchievementsByCategoryCode("MISSION");

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategoryCode()).isEqualTo("MISSION");
        }
    }

    @Nested
    @DisplayName("getUserAchievements 테스트")
    class GetUserAchievementsTest {

        @Test
        @DisplayName("사용자의 업적 목록을 조회한다")
        void getUserAchievements_success() {
            // given
            Achievement achievement = createTestAchievement(1L, "FIRST_MISSION_COMPLETE", 1, 50);
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(userAchievementRepository.findByUserIdWithAchievement(TEST_USER_ID))
                .thenReturn(List.of(userAchievement));

            // when
            List<UserAchievementResponse> result = achievementService.getUserAchievements(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("FIRST_MISSION_COMPLETE");
        }

        @Test
        @DisplayName("QA-149: mission_category_name 컬럼이 NULL 이어도 메타에서 lookup 한 이름이 응답에 채워진다")
        void getUserAchievements_missionCategoryName_filledFromMeta() {
            // given: achievement.missionCategoryName 은 NULL, mission_category_id=5 만 저장된 데이터.
            //        메타에 id=5 가 "독서" 로 등록되어 있으면 응답에 "독서" 가 채워져야 한다.
            Achievement achievement = Achievement.builder()
                .name("Reading 카테고리 경험치 7500")
                .description("Reading 카테고리에서 경험치 7500을 획득하세요")
                .categoryCode("MISSION")
                .requiredCount(7500)
                .rewardExp(0)
                .isActive(true)
                .isHidden(false)
                .checkLogicDataSource("USER_CATEGORY_EXPERIENCE")
                .checkLogicDataField("categoryExp")
                .comparisonOperator("GTE")
                .missionCategoryId(5L)
                .build();
            setId(achievement, 267L);
            UserAchievement ua = createTestUserAchievement(1221L, TEST_USER_ID, achievement, 686, false);

            when(userAchievementRepository.findByUserIdWithAchievement(TEST_USER_ID))
                .thenReturn(List.of(ua));
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(
                MissionCategoryResponse.builder().id(5L).name("독서").isActive(true).build()
            ));

            // when
            List<UserAchievementResponse> result = achievementService.getUserAchievements(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMissionCategoryId()).isEqualTo(5L);
            assertThat(result.get(0).getMissionCategoryName()).isEqualTo("독서");
        }

        @Test
        @DisplayName("QA-145: USER_CATEGORY_EXPERIENCE 업적 중 mission_category_id 가 메타에 없으면 응답에서 제외된다")
        void getUserAchievements_orphanedCategoryAchievement_filteredOut() {
            // given: 활성 메타 카테고리는 1(운동), 11(기타) 뿐. id=6 은 메타에서 사라진 옛 카테고리.
            Achievement aliveCategoryAchievement = createCategoryAchievement(1L, "운동 마스터", 1L);
            Achievement orphanedCategoryAchievement = createCategoryAchievement(2L, "사회활동 1000", 6L);
            Achievement nonCategoryAchievement = createTestAchievement(3L, "FIRST_MISSION_COMPLETE", 1, 50);
            UserAchievement ua1 = createTestUserAchievement(1L, TEST_USER_ID, aliveCategoryAchievement, 1, true);
            UserAchievement ua2 = createTestUserAchievement(2L, TEST_USER_ID, orphanedCategoryAchievement, 1, false);
            UserAchievement ua3 = createTestUserAchievement(3L, TEST_USER_ID, nonCategoryAchievement, 1, true);

            when(userAchievementRepository.findByUserIdWithAchievement(TEST_USER_ID))
                .thenReturn(List.of(ua1, ua2, ua3));
            when(missionCategoryService.getActiveCategories()).thenReturn(List.of(
                MissionCategoryResponse.builder().id(1L).name("운동").isActive(true).build(),
                MissionCategoryResponse.builder().id(11L).name("기타").isActive(true).build()
            ));

            // when
            List<UserAchievementResponse> result = achievementService.getUserAchievements(TEST_USER_ID);

            // then: 사라진 카테고리(id=6) 업적만 제외, 다른 dataSource 업적은 영향 없음
            assertThat(result).extracting(UserAchievementResponse::getName)
                .containsExactlyInAnyOrder("운동 마스터", "FIRST_MISSION_COMPLETE");
        }

        private Achievement createCategoryAchievement(Long id, String name, Long missionCategoryId) {
            Achievement achievement = Achievement.builder()
                .name(name)
                .description(name + " 설명")
                .categoryCode("MISSION")
                .requiredCount(1000)
                .rewardExp(100)
                .isActive(true)
                .isHidden(false)
                .checkLogicDataSource("USER_CATEGORY_EXPERIENCE")
                .checkLogicDataField("categoryExp")
                .comparisonOperator("GTE")
                .missionCategoryId(missionCategoryId)
                .build();
            setId(achievement, id);
            return achievement;
        }
    }

    @Nested
    @DisplayName("claimReward 테스트")
    class ClaimRewardTest {

        @Test
        @DisplayName("업적 보상을 수령한다")
        void claimReward_success() {
            // given
            Long achievementId = 1L;
            Achievement achievement = createTestAchievement(achievementId, "FIRST_MISSION_COMPLETE", 1, 50);
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, achievementId))
                .thenReturn(Optional.of(userAchievement));

            // when
            UserAchievementResponse result = achievementService.claimReward(TEST_USER_ID, achievementId);

            // then
            assertThat(result).isNotNull();
            assertThat(userAchievement.getIsRewardClaimed()).isTrue();
            verify(userExperienceService).addExperience(
                eq(TEST_USER_ID), eq(50), eq(ExpSourceType.ACHIEVEMENT), eq(achievementId), anyString(), eq("기타"));
        }

        @Test
        @DisplayName("칭호 보상이 있으면 칭호를 부여한다")
        void claimReward_withTitleReward() {
            // given
            Long achievementId = 1L;
            Long rewardTitleId = 10L;
            Achievement achievement = createTestAchievement(achievementId, "FIRST_MISSION_COMPLETE", 1, 50);
            achievement.setRewardTitleId(rewardTitleId);
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, achievementId))
                .thenReturn(Optional.of(userAchievement));

            // when
            achievementService.claimReward(TEST_USER_ID, achievementId);

            // then
            verify(titleService).grantTitle(TEST_USER_ID, rewardTitleId);
        }

        @Test
        @DisplayName("존재하지 않는 업적을 수령하면 예외가 발생한다")
        void claimReward_notFound_throwsException() {
            // given
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 999L))
                .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> achievementService.claimReward(TEST_USER_ID, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("업적을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("syncUserAchievements 테스트")
    class SyncUserAchievementsTest {

        @Test
        @DisplayName("업적 동기화 시 모든 동적 업적을 체크한다")
        void syncUserAchievements_checksAllDynamicAchievements() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_COMPLETE_10", 10, 100);

            when(achievementCacheService.getAchievementsWithCheckLogic())
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), any(Achievement.class))).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), any(Achievement.class))).thenReturn(5);
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of());
            when(userAchievementRepository.findByUserIdAndAchievementId(anyString(), anyLong()))
                .thenReturn(Optional.empty());
            when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID))
                .thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then
            verify(achievementCacheService).getAchievementsWithCheckLogic();
            verify(strategyRegistry).getStrategy("USER_STATS");
        }
    }

    @Nested
    @DisplayName("checkAllDynamicAchievements 테스트")
    class CheckAllDynamicAchievementsTest {

        @Test
        @DisplayName("조건이 충족되면 업적을 완료 처리한다")
        void checkAllDynamicAchievements_completesAchievement() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_COMPLETE_10", 10, 100);
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 5, false);

            when(achievementCacheService.getAchievementsWithCheckLogic())
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(15);
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID))
                .thenReturn(List.of(userAchievement));

            // when
            achievementService.checkAllDynamicAchievements(TEST_USER_ID);

            // then
            verify(mockStrategy).checkCondition(any(AchievementSyncContext.class), eq(achievement));
            assertThat(userAchievement.getCurrentCount()).isEqualTo(15);
            assertThat(userAchievement.getIsCompleted()).isTrue();
        }

        @Test
        @DisplayName("이미 완료된 업적은 스킵한다")
        void checkAllDynamicAchievements_skipsCompletedAchievement() {
            // given
            Achievement achievement = createTestAchievement(1L, "FIRST_MISSION_COMPLETE", 1, 50);
            UserAchievement completedAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(achievementCacheService.getAchievementsWithCheckLogic())
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID))
                .thenReturn(List.of(completedAchievement));
            // 이미 완료된 행은 currentCount stale 보정만 수행 — fetchCurrentValue 호출은 OK, checkCondition 은 호출되지 않아야 함
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(1);

            // when
            achievementService.checkAllDynamicAchievements(TEST_USER_ID);

            // then
            verify(mockStrategy, never()).checkCondition(any(AchievementSyncContext.class), any(Achievement.class));
        }

        @Test
        @DisplayName("Strategy가 없는 데이터 소스는 스킵한다")
        void checkAllDynamicAchievements_skipsUnknownDataSource() {
            // given
            Achievement achievement = createTestAchievement(1L, "UNKNOWN_ACHIEVEMENT", 1, 50);

            when(achievementCacheService.getAchievementsWithCheckLogic())
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(null);
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.checkAllDynamicAchievements(TEST_USER_ID);

            // then
            verify(userAchievementRepository, never()).findByUserIdAndAchievementId(anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("autoClaimRewards 테스트")
    class AutoClaimRewardsTest {

        @Test
        @DisplayName("수령 가능한 업적 보상을 자동으로 수령한다")
        void autoClaimRewards_claimsAllClaimable() {
            // given
            Achievement achievement = createTestAchievement(1L, "FIRST_MISSION_COMPLETE", 1, 50);
            UserAchievement claimableAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID))
                .thenReturn(List.of(claimableAchievement));
            when(userAchievementRepository.save(any(UserAchievement.class)))
                .thenReturn(claimableAchievement);

            // when
            achievementService.autoClaimRewards(TEST_USER_ID);

            // then
            assertThat(claimableAchievement.getIsRewardClaimed()).isTrue();
            verify(userExperienceService).addExperience(
                eq(TEST_USER_ID), eq(50), eq(ExpSourceType.ACHIEVEMENT), eq(1L), anyString(), eq("기타"));
            verify(userAchievementRepository).save(claimableAchievement);
        }
    }

    @Nested
    @DisplayName("getCompletedAchievements 테스트")
    class GetCompletedAchievementsTest {

        @Test
        @DisplayName("완료된 업적 목록을 조회한다")
        void getCompletedAchievements_success() {
            // given
            Achievement achievement = createTestAchievement(1L, "FIRST_MISSION_COMPLETE", 1, 50);
            UserAchievement completedAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(userAchievementRepository.findCompletedByUserId(TEST_USER_ID))
                .thenReturn(List.of(completedAchievement));

            // when
            List<UserAchievementResponse> result = achievementService.getCompletedAchievements(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("getClaimableAchievements 테스트")
    class GetClaimableAchievementsTest {

        @Test
        @DisplayName("수령 가능한 업적 목록을 조회한다")
        void getClaimableAchievements_success() {
            // given
            Achievement achievement = createTestAchievement(1L, "FIRST_MISSION_COMPLETE", 1, 50);
            UserAchievement claimableAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID))
                .thenReturn(List.of(claimableAchievement));

            // when
            List<UserAchievementResponse> result = achievementService.getClaimableAchievements(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getAchievementsByMissionCategoryId 테스트")
    class GetAchievementsByMissionCategoryIdTest {

        @Test
        @DisplayName("미션 카테고리 ID로 업적을 조회한다")
        void getAchievementsByMissionCategoryId_success() {
            // given
            Long missionCategoryId = 1L;
            Achievement achievement = createTestAchievement(1L, "CATEGORY_MISSION_COMPLETE", 10, 100);

            when(achievementCacheService.getAchievementsByMissionCategoryId(missionCategoryId))
                .thenReturn(List.of(achievement));

            // when
            List<AchievementResponse> result = achievementService.getAchievementsByMissionCategoryId(missionCategoryId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("CATEGORY_MISSION_COMPLETE");
        }

        @Test
        @DisplayName("해당 카테고리의 업적이 없으면 빈 목록을 반환한다")
        void getAchievementsByMissionCategoryId_empty() {
            // given
            Long missionCategoryId = 999L;
            when(achievementCacheService.getAchievementsByMissionCategoryId(missionCategoryId))
                .thenReturn(List.of());

            // when
            List<AchievementResponse> result = achievementService.getAchievementsByMissionCategoryId(missionCategoryId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getInProgressAchievements 테스트")
    class GetInProgressAchievementsTest {

        @Test
        @DisplayName("진행 중인 업적 목록을 조회한다")
        void getInProgressAchievements_success() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_COMPLETE_10", 10, 100);
            UserAchievement inProgressAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 5, false);

            when(userAchievementRepository.findInProgressByUserId(TEST_USER_ID))
                .thenReturn(List.of(inProgressAchievement));

            // when
            List<UserAchievementResponse> result = achievementService.getInProgressAchievements(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsCompleted()).isFalse();
            assertThat(result.get(0).getCurrentCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("진행 중인 업적이 없으면 빈 목록을 반환한다")
        void getInProgressAchievements_empty() {
            // given
            when(userAchievementRepository.findInProgressByUserId(TEST_USER_ID))
                .thenReturn(List.of());

            // when
            List<UserAchievementResponse> result = achievementService.getInProgressAchievements(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("checkAchievementsByDataSource 테스트")
    class CheckAchievementsByDataSourceTest {

        @Test
        @DisplayName("특정 데이터 소스의 업적을 체크한다")
        void checkAchievementsByDataSource_success() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_COMPLETE_10", 10, 100);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(TEST_USER_ID, achievement)).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, achievement)).thenReturn(10);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.empty());
            when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then
            verify(achievementCacheService).getAchievementsByDataSource("USER_STATS");
            verify(strategyRegistry).getStrategy("USER_STATS");
        }

        @Test
        @DisplayName("Strategy가 없으면 스킵한다")
        void checkAchievementsByDataSource_noStrategy() {
            // given
            when(strategyRegistry.getStrategy("UNKNOWN_SOURCE")).thenReturn(null);

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "UNKNOWN_SOURCE");

            // then
            verify(achievementRepository, never()).findByCheckLogicDataSourceAndIsActiveTrue(anyString());
        }
    }

    // =============================================
    // 추가 테스트: 미커버 분기 커버
    // =============================================

    @Nested
    @DisplayName("checkAndUpdateAchievementDynamic 분기 테스트")
    class CheckAndUpdateAchievementDynamicTest {

        @Test
        @DisplayName("비활성 업적은 조건 체크 없이 스킵한다")
        void dynamic_inactiveAchievement_skips() {
            // given
            Achievement achievement = createTestAchievement(1L, "INACTIVE_ACH", 10, 0);
            achievement.setIsActive(false);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then
            verify(mockStrategy, never()).checkCondition(anyString(), any(Achievement.class));
            verify(userAchievementRepository, never()).findByUserIdAndAchievementId(anyString(), anyLong());
        }

        @Test
        @DisplayName("이미 완료된 업적 — currentCount가 stale할 때 Number값으로 갱신한다")
        void dynamic_alreadyCompleted_staleSyncWithNumber() {
            // given
            Achievement achievement = createTestAchievement(1L, "COMPLETED_ACH", 5, 0);
            UserAchievement completedUa = createTestUserAchievement(1L, TEST_USER_ID, achievement, 5, true);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(completedUa));
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, achievement)).thenReturn(8);

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then: currentCount가 8로 갱신
            assertThat(completedUa.getCurrentCount()).isEqualTo(8);
            verify(mockStrategy, never()).checkCondition(anyString(), any(Achievement.class));
        }

        @Test
        @DisplayName("이미 완료된 업적 — fetchCurrentValue가 Number가 아니면 갱신하지 않는다")
        void dynamic_alreadyCompleted_nonNumberValueSkips() {
            // given
            Achievement achievement = createTestAchievement(1L, "COMPLETED_BOOL_ACH", 1, 0);
            UserAchievement completedUa = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(completedUa));
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, achievement)).thenReturn("NOT_A_NUMBER");

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then: currentCount 변화 없음
            assertThat(completedUa.getCurrentCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 완료된 업적 — currentCount가 이미 최신값과 같으면 setCount를 호출하지 않는다")
        void dynamic_alreadyCompleted_noOpWhenCountUnchanged() {
            // given
            Achievement achievement = createTestAchievement(1L, "COMPLETED_ACH", 5, 0);
            UserAchievement completedUa = createTestUserAchievement(1L, TEST_USER_ID, achievement, 5, true);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(completedUa));
            // 현재값과 동일한 5 반환
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, achievement)).thenReturn(5);

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then: isCompleted 상태 유지, setCount는 호출되지만 값은 동일
            assertThat(completedUa.getIsCompleted()).isTrue();
        }

        @Test
        @DisplayName("조건 충족 — fetchCurrentValue가 Number일 때 진행도를 갱신하고 완료 처리한다")
        void dynamic_conditionMet_numberValue_completesAchievement() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 50);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.empty());
            when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
            when(mockStrategy.checkCondition(TEST_USER_ID, achievement)).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, achievement)).thenReturn(10);

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then
            verify(userStatsService).recordAchievementCompleted(TEST_USER_ID);
            verify(eventPublisher).publishEvent(any(io.pinkspider.global.event.AchievementCompletedEvent.class));
        }

        @Test
        @DisplayName("조건 충족 — fetchCurrentValue가 Boolean true일 때 count를 1로 설정한다")
        void dynamic_conditionMet_booleanValue_setsCountTo1() {
            // given
            Achievement achievement = createTestAchievement(1L, "BOOLEAN_ACH", 1, 30);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.empty());
            when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
            when(mockStrategy.checkCondition(TEST_USER_ID, achievement)).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, achievement)).thenReturn(Boolean.TRUE);

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then: count가 1로 설정되어 requiredCount(1)에 도달 → 완료
            verify(userStatsService).recordAchievementCompleted(TEST_USER_ID);
        }

        @Test
        @DisplayName("조건 충족 — isHidden 업적은 이벤트를 발행하지 않는다")
        void dynamic_conditionMet_hiddenAchievement_noEventPublished() {
            // given
            Achievement achievement = createTestAchievement(1L, "HIDDEN_ACH", 1, 0);
            achievement.setIsHidden(true);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.empty());
            when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
            when(mockStrategy.checkCondition(TEST_USER_ID, achievement)).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, achievement)).thenReturn(1);

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then: 완료 처리는 되지만 이벤트 발행은 없음
            verify(userStatsService).recordAchievementCompleted(TEST_USER_ID);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("조건 충족 — 기존 userAchievement가 존재하면 새로 생성하지 않는다")
        void dynamic_conditionMet_existingUserAchievement_usesExisting() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 0);
            UserAchievement existing = createTestUserAchievement(1L, TEST_USER_ID, achievement, 7, false);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(existing));
            when(mockStrategy.checkCondition(TEST_USER_ID, achievement)).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, achievement)).thenReturn(10);

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then: saveAndFlush 호출 없음 (기존 엔티티 재사용)
            verify(userAchievementRepository, never()).saveAndFlush(any());
            assertThat(existing.getCurrentCount()).isEqualTo(10);
            assertThat(existing.getIsCompleted()).isTrue();
        }

        @Test
        @DisplayName("조건 미충족 — fetchCurrentValue가 Number일 때 진행도를 갱신한다")
        void dynamic_conditionNotMet_numberValue_updatesProgress() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 0);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.empty());
            when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
            when(mockStrategy.checkCondition(TEST_USER_ID, achievement)).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, achievement)).thenReturn(5);

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then: 완료 처리 없음, 이벤트 없음
            verify(userStatsService, never()).recordAchievementCompleted(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("조건 미충족 — fetchCurrentValue가 Number가 아니면 진행도를 갱신하지 않는다")
        void dynamic_conditionNotMet_nonNumberValue_noUpdate() {
            // given
            Achievement achievement = createTestAchievement(1L, "BOOL_ACH", 1, 0);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.empty());
            when(mockStrategy.checkCondition(TEST_USER_ID, achievement)).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, achievement)).thenReturn(Boolean.FALSE);

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then: saveAndFlush 호출 없음 (Number가 아니므로 UserAchievement 생성 안 됨)
            verify(userAchievementRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("조건 미충족 — 기존 userAchievement가 있으면 재사용하여 진행도를 갱신한다")
        void dynamic_conditionNotMet_existingUserAchievement_updatesProgress() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 0);
            UserAchievement existing = createTestUserAchievement(1L, TEST_USER_ID, achievement, 3, false);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(existing));
            when(mockStrategy.checkCondition(TEST_USER_ID, achievement)).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, achievement)).thenReturn(6);

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then
            assertThat(existing.getCurrentCount()).isEqualTo(6);
            verify(userAchievementRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Race condition — saveAndFlush에서 DataIntegrityViolationException 발생 시 기존 레코드를 조회한다")
        void dynamic_raceCondition_duplicateKey_fallbackToExisting() {
            // given
            // checkAndUpdateAchievementDynamic 흐름:
            //   1. findByUserIdAndAchievementId → empty (미완료 판단)
            //   2. conditionMet=true → getOrCreateUserAchievement 호출
            //      → findByUserIdAndAchievementId (2nd) → empty → saveAndFlush → DataIntegrityViolationException
            //      → findByUserIdAndAchievementId (3rd) → 기존 레코드 반환
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 0);
            UserAchievement existingAfterRace = createTestUserAchievement(1L, TEST_USER_ID, achievement, 0, false);

            when(achievementCacheService.getAchievementsByDataSource("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(TEST_USER_ID, achievement)).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, achievement)).thenReturn(10);
            // 1st: empty(미완료 판단), 2nd: empty(getOrCreate 내 첫 조회), 3rd: 기존 레코드(fallback)
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingAfterRace));
            when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then: race condition 후 기존 레코드 count 갱신
            assertThat(existingAfterRace.getCurrentCount()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("checkAndUpdateAchievementWithContext 분기 테스트")
    class CheckAndUpdateAchievementWithContextTest {

        private UserStats buildUserStats() {
            return UserStats.builder()
                .userId(TEST_USER_ID)
                .totalMissionCompletions(10)
                .build();
        }

        private void setupBuildSyncContextMocks() {
            when(userStatsRepository.findByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(buildUserStats()));
            when(userExperienceRepository.findByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(userCategoryExperienceRepository.findByUserIdOrderByTotalExpDesc(TEST_USER_ID))
                .thenReturn(List.of());
            when(guildQueryFacade.getUserGuildMemberships(TEST_USER_ID))
                .thenReturn(List.of());
        }

        @Test
        @DisplayName("비활성 업적은 컨텍스트 기반 체크에서도 스킵한다")
        void withCtx_inactiveAchievement_skips() {
            // given
            Achievement achievement = createTestAchievement(1L, "INACTIVE_ACH", 5, 0);
            achievement.setIsActive(false);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of());
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then
            verify(mockStrategy, never()).checkCondition(any(AchievementSyncContext.class), any(Achievement.class));
        }

        @Test
        @DisplayName("이미 완료된 업적 — stale currentCount를 Number값으로 갱신한다")
        void withCtx_alreadyCompleted_staleSyncWithNumber() {
            // given
            Achievement achievement = createTestAchievement(1L, "COMPLETED_ACH", 5, 0);
            UserAchievement completedUa = createTestUserAchievement(1L, TEST_USER_ID, achievement, 5, true);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID))
                .thenReturn(List.of(completedUa));
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(9);
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then
            assertThat(completedUa.getCurrentCount()).isEqualTo(9);
            verify(mockStrategy, never()).checkCondition(any(AchievementSyncContext.class), any(Achievement.class));
        }

        @Test
        @DisplayName("이미 완료된 업적 — currentCount 최신값과 동일하면 setCount 변경 없음")
        void withCtx_alreadyCompleted_noOpWhenCountSame() {
            // given
            Achievement achievement = createTestAchievement(1L, "COMPLETED_ACH", 5, 0);
            UserAchievement completedUa = createTestUserAchievement(1L, TEST_USER_ID, achievement, 5, true);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID))
                .thenReturn(List.of(completedUa));
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(5);
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then: isCompleted 상태 유지
            assertThat(completedUa.getIsCompleted()).isTrue();
            assertThat(completedUa.getCurrentCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("이미 완료된 업적 — fetchCurrentValue가 Number가 아니면 갱신하지 않는다")
        void withCtx_alreadyCompleted_nonNumberSkips() {
            // given
            Achievement achievement = createTestAchievement(1L, "COMPLETED_BOOL", 1, 0);
            UserAchievement completedUa = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID))
                .thenReturn(List.of(completedUa));
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn("TEXT");
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then: count 변화 없음
            assertThat(completedUa.getCurrentCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("조건 충족 — Number 반환값으로 진행도 갱신하고 완료 처리한다")
        void withCtx_conditionMet_numberValue_completesAchievement() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 50);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of());
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(10);
            when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then
            verify(userStatsService).recordAchievementCompleted(TEST_USER_ID);
            verify(eventPublisher).publishEvent(any(io.pinkspider.global.event.AchievementCompletedEvent.class));
        }

        @Test
        @DisplayName("조건 충족 — Boolean true 반환값으로 count를 1로 설정하고 완료 처리한다")
        void withCtx_conditionMet_booleanValue_setsCountTo1() {
            // given
            Achievement achievement = createTestAchievement(1L, "BOOLEAN_ACH", 1, 30);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of());
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(Boolean.TRUE);
            when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then
            verify(userStatsService).recordAchievementCompleted(TEST_USER_ID);
        }

        @Test
        @DisplayName("조건 충족 — fetchCurrentValue가 Number/Boolean 모두 아니면 완료 처리를 하지 않는다")
        void withCtx_conditionMet_unknownValueType_doesNotComplete() {
            // given
            // 기존 userAchievement가 있으면 getOrCreate 없이 바로 fetchCurrentValue 분기로 진입
            Achievement achievement = createTestAchievement(1L, "UNKNOWN_TYPE_ACH", 1, 0);
            UserAchievement existing = createTestUserAchievement(1L, TEST_USER_ID, achievement, 0, false);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of(existing));
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn("UNKNOWN");
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then: 완료 처리 없음 (Number/Boolean이 아니므로 return)
            verify(userStatsService, never()).recordAchievementCompleted(any());
        }

        @Test
        @DisplayName("조건 충족 — isHidden 업적은 이벤트를 발행하지 않는다")
        void withCtx_conditionMet_hiddenAchievement_noEventPublished() {
            // given
            Achievement achievement = createTestAchievement(1L, "HIDDEN_CTX_ACH", 1, 0);
            achievement.setIsHidden(true);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of());
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(1);
            when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then
            verify(userStatsService).recordAchievementCompleted(TEST_USER_ID);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("조건 충족 — currentCount가 이미 최신값과 동일하면 setCount를 호출하지 않는다")
        void withCtx_conditionMet_noOpWhenCountUnchanged() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 0);
            UserAchievement existing = createTestUserAchievement(1L, TEST_USER_ID, achievement, 10, true);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of(existing));
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            // 이미 완료된 행 → fetchCurrentValue 호출 경로 (stale 보정)
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(10);
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then: count 변화 없음, checkCondition 호출 없음
            assertThat(existing.getCurrentCount()).isEqualTo(10);
            verify(mockStrategy, never()).checkCondition(any(AchievementSyncContext.class), any(Achievement.class));
        }

        @Test
        @DisplayName("조건 충족 — 기존 userAchievement가 있으면 getOrCreate 없이 재사용한다")
        void withCtx_conditionMet_existingUserAchievement_usesExisting() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 0);
            UserAchievement existing = createTestUserAchievement(1L, TEST_USER_ID, achievement, 7, false);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of(existing));
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(10);
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then
            assertThat(existing.getCurrentCount()).isEqualTo(10);
            assertThat(existing.getIsCompleted()).isTrue();
            verify(userAchievementRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("조건 미충족 — Number값으로 진행도만 갱신한다")
        void withCtx_conditionNotMet_numberValue_updatesProgress() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 0);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of());
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(4);
            when(userAchievementRepository.saveAndFlush(any(UserAchievement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then
            verify(userStatsService, never()).recordAchievementCompleted(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("조건 미충족 — Number가 아닌 값이면 UserAchievement를 생성하지 않는다")
        void withCtx_conditionNotMet_nonNumberValue_noCreate() {
            // given
            Achievement achievement = createTestAchievement(1L, "BOOL_ACH", 1, 0);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of());
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(Boolean.FALSE);
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then: saveAndFlush 없음
            verify(userAchievementRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("조건 미충족 — 기존 userAchievement가 있으면 재사용하여 count를 갱신한다")
        void withCtx_conditionNotMet_existingUserAchievement_updatesCount() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 0);
            UserAchievement existing = createTestUserAchievement(1L, TEST_USER_ID, achievement, 3, false);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of(existing));
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(6);
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then
            assertThat(existing.getCurrentCount()).isEqualTo(6);
            verify(userAchievementRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("조건 미충족 — count가 동일하면 setCount를 호출하지 않는다")
        void withCtx_conditionNotMet_noOpWhenCountSame() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 0);
            UserAchievement existing = createTestUserAchievement(1L, TEST_USER_ID, achievement, 6, false);

            setupBuildSyncContextMocks();
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of(existing));
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(6);
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then: 값 동일하므로 상태 변화 없음
            assertThat(existing.getCurrentCount()).isEqualTo(6);
            assertThat(existing.getIsCompleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("buildSyncContext 분기 테스트")
    class BuildSyncContextTest {

        @Test
        @DisplayName("UserStats, UserExperience가 없어도 컨텍스트를 생성한다")
        void buildSyncContext_allOptionalAbsent() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 0);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userCategoryExperienceRepository.findByUserIdOrderByTotalExpDesc(TEST_USER_ID))
                .thenReturn(List.of());
            when(guildQueryFacade.getUserGuildMemberships(TEST_USER_ID)).thenReturn(List.of());
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of());
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(0);
            when(userAchievementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when & then: 예외 없이 정상 동작
            assertThat(achievementService.syncUserAchievements(TEST_USER_ID)).isTrue();
        }

        @Test
        @DisplayName("길드 마스터가 있으면 컨텍스트에 guildMaster=true로 설정된다")
        void buildSyncContext_guildMasterPresent() {
            // given
            Achievement achievement = createTestAchievement(1L, "GUILD_MASTER_ACH", 1, 0);
            io.pinkspider.global.facade.dto.GuildMembershipInfo masterInfo =
                new io.pinkspider.global.facade.dto.GuildMembershipInfo(1L, "테스트 길드", null, 1, true, false);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userCategoryExperienceRepository.findByUserIdOrderByTotalExpDesc(TEST_USER_ID))
                .thenReturn(List.of());
            when(guildQueryFacade.getUserGuildMemberships(TEST_USER_ID)).thenReturn(List.of(masterInfo));
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of());
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(0);
            when(userAchievementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when & then: guildMaster 여부가 컨텍스트에 포함되어 정상 실행
            assertThat(achievementService.syncUserAchievements(TEST_USER_ID)).isTrue();
        }

        @Test
        @DisplayName("guildQueryFacade 예외 발생 시 guildMaster=false로 컨텍스트를 생성한다")
        void buildSyncContext_guildFacadeException_defaultsToFalse() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 0);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userCategoryExperienceRepository.findByUserIdOrderByTotalExpDesc(TEST_USER_ID))
                .thenReturn(List.of());
            when(guildQueryFacade.getUserGuildMemberships(TEST_USER_ID))
                .thenThrow(new RuntimeException("서비스 연결 실패"));
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of());
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(0);
            when(userAchievementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when: 예외 발생해도 guildMaster=false로 정상 진행
            assertThat(achievementService.syncUserAchievements(TEST_USER_ID)).isTrue();
        }

        @Test
        @DisplayName("guildQueryFacade가 null을 반환하면 guildMaster=false로 설정한다")
        void buildSyncContext_guildFacadeReturnsNull_defaultsToFalse() {
            // given
            Achievement achievement = createTestAchievement(1L, "MISSION_10", 10, 0);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userCategoryExperienceRepository.findByUserIdOrderByTotalExpDesc(TEST_USER_ID))
                .thenReturn(List.of());
            when(guildQueryFacade.getUserGuildMemberships(TEST_USER_ID)).thenReturn(null);
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID)).thenReturn(List.of());
            when(achievementCacheService.getAchievementsWithCheckLogic()).thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(any(AchievementSyncContext.class), eq(achievement))).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(any(AchievementSyncContext.class), eq(achievement))).thenReturn(0);
            when(userAchievementRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID)).thenReturn(List.of());

            // when & then
            assertThat(achievementService.syncUserAchievements(TEST_USER_ID)).isTrue();
        }
    }

    @Nested
    @DisplayName("claimReward / claimRewardInternal 추가 분기 테스트")
    class ClaimRewardAdditionalTest {

        @Test
        @DisplayName("rewardExp가 0이면 경험치 지급을 호출하지 않는다")
        void claimReward_zeroExp_noExpGranted() {
            // given
            Long achievementId = 2L;
            Achievement achievement = createTestAchievement(achievementId, "ZERO_EXP_ACH", 1, 0);
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, achievementId))
                .thenReturn(Optional.of(userAchievement));

            // when
            achievementService.claimReward(TEST_USER_ID, achievementId);

            // then: 경험치 지급 없음
            verify(userExperienceService, never()).addExperience(any(), anyInt(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("rewardTitleId가 null이면 칭호 부여를 호출하지 않는다")
        void claimReward_noTitle_noGrantTitle() {
            // given
            Long achievementId = 3L;
            Achievement achievement = createTestAchievement(achievementId, "NO_TITLE_ACH", 1, 100);
            // rewardTitleId = null (기본값)
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, achievementId))
                .thenReturn(Optional.of(userAchievement));

            // when
            achievementService.claimReward(TEST_USER_ID, achievementId);

            // then
            verify(titleService, never()).grantTitle(any(), any());
        }

        @Test
        @DisplayName("autoClaimRewards — rewardExp가 0이면 경험치 지급 없이 저장한다")
        void autoClaimRewards_zeroExp_noExpGranted() {
            // given
            Achievement achievement = createTestAchievement(1L, "ZERO_EXP_ACH", 1, 0);
            UserAchievement claimable = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID))
                .thenReturn(List.of(claimable));
            when(userAchievementRepository.save(any())).thenReturn(claimable);

            // when
            achievementService.autoClaimRewards(TEST_USER_ID);

            // then
            verify(userExperienceService, never()).addExperience(any(), anyInt(), any(), any(), any(), any());
            verify(userAchievementRepository).save(claimable);
        }

        @Test
        @DisplayName("autoClaimRewards — rewardTitleId가 있으면 칭호를 부여한다")
        void autoClaimRewards_withTitle_grantsTitle() {
            // given
            Achievement achievement = createTestAchievement(1L, "TITLE_ACH", 1, 0);
            achievement.setRewardTitleId(99L);
            UserAchievement claimable = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID))
                .thenReturn(List.of(claimable));
            when(userAchievementRepository.save(any())).thenReturn(claimable);

            // when
            achievementService.autoClaimRewards(TEST_USER_ID);

            // then
            verify(titleService).grantTitle(TEST_USER_ID, 99L);
        }

        @Test
        @DisplayName("autoClaimRewards — claimRewardInternal에서 예외 발생 시 다른 업적은 계속 처리한다")
        void autoClaimRewards_exceptionInOne_continuesWithOthers() {
            // given
            Achievement ach1 = createTestAchievement(1L, "ACH_1", 1, 0);
            Achievement ach2 = createTestAchievement(2L, "ACH_2", 1, 50);
            // ach1은 isCompleted=false → claimReward() 내부에서 IllegalStateException
            UserAchievement notCompleted = createTestUserAchievement(1L, TEST_USER_ID, ach1, 0, false);
            UserAchievement claimable2 = createTestUserAchievement(2L, TEST_USER_ID, ach2, 1, true);

            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID))
                .thenReturn(List.of(notCompleted, claimable2));
            when(userAchievementRepository.save(any())).thenReturn(claimable2);

            // when
            achievementService.autoClaimRewards(TEST_USER_ID);

            // then: ach2 보상은 정상 수령
            assertThat(claimable2.getIsRewardClaimed()).isTrue();
        }

        @Test
        @DisplayName("syncUserAchievements — buildSyncContext 내 예외 발생 시 false를 반환한다")
        void syncUserAchievements_internalException_returnsFalse() {
            // given
            // buildSyncContext 순서: userStatsRepo → userExperienceRepo → categoryExpRepo → guildFacade → findAllByUserIdForSync
            // findAllByUserIdForSync에서 예외 발생 → syncUserAchievements catch → false 반환
            // guildQueryFacade는 Mockito 기본값(빈 List)이므로 별도 stubbing 불필요
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(userCategoryExperienceRepository.findByUserIdOrderByTotalExpDesc(TEST_USER_ID))
                .thenReturn(List.of());
            when(userAchievementRepository.findAllByUserIdForSync(TEST_USER_ID))
                .thenThrow(new RuntimeException("DB 연결 실패"));

            // when
            boolean result = achievementService.syncUserAchievements(TEST_USER_ID);

            // then
            assertThat(result).isFalse();
        }
    }
}
