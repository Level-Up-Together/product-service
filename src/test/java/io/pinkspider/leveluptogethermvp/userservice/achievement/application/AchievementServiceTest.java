package io.pinkspider.leveluptogethermvp.userservice.achievement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy.AchievementCheckStrategy;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy.AchievementCheckStrategyRegistry;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ExpSourceType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserAchievementRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.AchievementResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.UserAchievementResponse;
import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import java.lang.reflect.Field;
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

    @InjectMocks
    private AchievementService achievementService;

    private static final String TEST_USER_ID = "test-user-123";

    private void setAchievementId(Achievement achievement, Long id) {
        try {
            Field idField = Achievement.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(achievement, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setUserAchievementId(UserAchievement userAchievement, Long id) {
        try {
            Field idField = UserAchievement.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(userAchievement, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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
        setAchievementId(achievement, id);
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
        setUserAchievementId(userAchievement, id);
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

            when(achievementRepository.findVisibleAchievements()).thenReturn(List.of(achievement1, achievement2));

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

            when(achievementRepository.findVisibleAchievementsByCategoryCode("MISSION"))
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

            when(achievementRepository.findAllWithCheckLogicAndIsActiveTrue())
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(anyString(), any(Achievement.class))).thenReturn(false);
            when(mockStrategy.fetchCurrentValue(anyString(), anyString())).thenReturn(5);
            when(userAchievementRepository.findByUserIdAndAchievementId(anyString(), anyLong()))
                .thenReturn(Optional.empty());
            when(userAchievementRepository.save(any(UserAchievement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            when(userAchievementRepository.findClaimableByUserId(TEST_USER_ID))
                .thenReturn(List.of());

            // when
            achievementService.syncUserAchievements(TEST_USER_ID);

            // then
            verify(achievementRepository).findAllWithCheckLogicAndIsActiveTrue();
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
            // 진행 중인 업적 (currentCount=5, 아직 미완료)
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 5, false);

            when(achievementRepository.findAllWithCheckLogicAndIsActiveTrue())
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(TEST_USER_ID, achievement)).thenReturn(true);
            // fetchCurrentValue가 10 이상 반환하면 setCount 후 completion이 트리거됨
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, "totalMissionCompletions")).thenReturn(15);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(userAchievement));

            // when
            achievementService.checkAllDynamicAchievements(TEST_USER_ID);

            // then
            verify(mockStrategy).checkCondition(TEST_USER_ID, achievement);
            // setCount(15) 호출 후 userAchievement.currentCount == 15 >= requiredCount(10)이므로 완료됨
            assertThat(userAchievement.getCurrentCount()).isEqualTo(15);
            assertThat(userAchievement.getIsCompleted()).isTrue();
        }

        @Test
        @DisplayName("이미 완료된 업적은 스킵한다")
        void checkAllDynamicAchievements_skipsCompletedAchievement() {
            // given
            Achievement achievement = createTestAchievement(1L, "FIRST_MISSION_COMPLETE", 1, 50);
            UserAchievement completedAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(achievementRepository.findAllWithCheckLogicAndIsActiveTrue())
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(completedAchievement));

            // when
            achievementService.checkAllDynamicAchievements(TEST_USER_ID);

            // then
            verify(mockStrategy, never()).checkCondition(anyString(), any());
        }

        @Test
        @DisplayName("Strategy가 없는 데이터 소스는 스킵한다")
        void checkAllDynamicAchievements_skipsUnknownDataSource() {
            // given
            Achievement achievement = createTestAchievement(1L, "UNKNOWN_ACHIEVEMENT", 1, 50);

            when(achievementRepository.findAllWithCheckLogicAndIsActiveTrue())
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(null);

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

            when(achievementRepository.findVisibleAchievementsByMissionCategoryId(missionCategoryId))
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
            when(achievementRepository.findVisibleAchievementsByMissionCategoryId(missionCategoryId))
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

            when(achievementRepository.findByCheckLogicDataSourceAndIsActiveTrue("USER_STATS"))
                .thenReturn(List.of(achievement));
            when(strategyRegistry.getStrategy("USER_STATS")).thenReturn(mockStrategy);
            when(mockStrategy.checkCondition(TEST_USER_ID, achievement)).thenReturn(true);
            when(mockStrategy.fetchCurrentValue(TEST_USER_ID, "totalMissionCompletions")).thenReturn(10);
            when(userAchievementRepository.findByUserIdAndAchievementId(TEST_USER_ID, 1L))
                .thenReturn(Optional.empty());
            when(userAchievementRepository.save(any(UserAchievement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            achievementService.checkAchievementsByDataSource(TEST_USER_ID, "USER_STATS");

            // then
            verify(achievementRepository).findByCheckLogicDataSourceAndIsActiveTrue("USER_STATS");
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
}
