package io.pinkspider.leveluptogethermvp.userservice.achievement.application;

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

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.AchievementCategory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.AchievementType;
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

    private Achievement createTestAchievement(Long id, AchievementType type, int targetCount, int rewardExp) {
        Achievement achievement = Achievement.builder()
            .name(type.getDisplayName())
            .description(type.getDisplayName() + " 설명")
            .achievementType(type)
            .category(AchievementCategory.MISSION)
            .requiredCount(targetCount)
            .rewardExp(rewardExp)
            .isActive(true)
            .isHidden(false)
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
            Achievement achievement1 = createTestAchievement(1L, AchievementType.FIRST_MISSION_COMPLETE, 1, 50);
            Achievement achievement2 = createTestAchievement(2L, AchievementType.MISSION_COMPLETE_10, 10, 100);

            when(achievementRepository.findVisibleAchievements()).thenReturn(List.of(achievement1, achievement2));

            // when
            List<AchievementResponse> result = achievementService.getAllAchievements();

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getAchievementsByCategory 테스트")
    class GetAchievementsByCategoryTest {

        @Test
        @DisplayName("카테고리별 업적을 조회한다")
        void getAchievementsByCategory_success() {
            // given
            Achievement achievement = createTestAchievement(1L, AchievementType.FIRST_MISSION_COMPLETE, 1, 50);

            when(achievementRepository.findVisibleAchievementsByCategory(AchievementCategory.MISSION))
                .thenReturn(List.of(achievement));

            // when
            List<AchievementResponse> result = achievementService.getAchievementsByCategory(AchievementCategory.MISSION);

            // then
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getUserAchievements 테스트")
    class GetUserAchievementsTest {

        @Test
        @DisplayName("사용자의 업적 목록을 조회한다")
        void getUserAchievements_success() {
            // given
            Achievement achievement = createTestAchievement(1L, AchievementType.FIRST_MISSION_COMPLETE, 1, 50);
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 1, true);

            when(userAchievementRepository.findByUserIdWithAchievement(TEST_USER_ID))
                .thenReturn(List.of(userAchievement));

            // when
            List<UserAchievementResponse> result = achievementService.getUserAchievements(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("updateAchievementProgress 테스트")
    class UpdateAchievementProgressTest {

        @Test
        @DisplayName("업적 진행도를 업데이트한다")
        void updateAchievementProgress_success() {
            // given
            Achievement achievement = createTestAchievement(1L, AchievementType.MISSION_COMPLETE_10, 10, 100);
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 5, false);

            when(achievementRepository.findByAchievementType(AchievementType.MISSION_COMPLETE_10))
                .thenReturn(Optional.of(achievement));
            when(userAchievementRepository.findByUserIdAndAchievementType(TEST_USER_ID, AchievementType.MISSION_COMPLETE_10))
                .thenReturn(Optional.of(userAchievement));

            // when
            achievementService.updateAchievementProgress(TEST_USER_ID, AchievementType.MISSION_COMPLETE_10, 7);

            // then
            assertThat(userAchievement.getCurrentCount()).isEqualTo(7);
        }

        @Test
        @DisplayName("업적이 없으면 업데이트하지 않는다")
        void updateAchievementProgress_noAchievement() {
            // given
            when(achievementRepository.findByAchievementType(AchievementType.MISSION_COMPLETE_10))
                .thenReturn(Optional.empty());

            // when
            achievementService.updateAchievementProgress(TEST_USER_ID, AchievementType.MISSION_COMPLETE_10, 5);

            // then
            verify(userAchievementRepository, never()).findByUserIdAndAchievementType(anyString(), any());
        }

        @Test
        @DisplayName("비활성화된 업적은 업데이트하지 않는다")
        void updateAchievementProgress_inactiveAchievement() {
            // given
            Achievement inactiveAchievement = createTestAchievement(1L, AchievementType.MISSION_COMPLETE_10, 10, 100);
            inactiveAchievement.setIsActive(false);

            when(achievementRepository.findByAchievementType(AchievementType.MISSION_COMPLETE_10))
                .thenReturn(Optional.of(inactiveAchievement));

            // when
            achievementService.updateAchievementProgress(TEST_USER_ID, AchievementType.MISSION_COMPLETE_10, 5);

            // then
            verify(userAchievementRepository, never()).findByUserIdAndAchievementType(anyString(), any());
        }

        @Test
        @DisplayName("완료된 업적은 업데이트하지 않는다")
        void updateAchievementProgress_alreadyCompleted() {
            // given
            Achievement achievement = createTestAchievement(1L, AchievementType.MISSION_COMPLETE_10, 10, 100);
            UserAchievement completedAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 10, true);

            when(achievementRepository.findByAchievementType(AchievementType.MISSION_COMPLETE_10))
                .thenReturn(Optional.of(achievement));
            when(userAchievementRepository.findByUserIdAndAchievementType(TEST_USER_ID, AchievementType.MISSION_COMPLETE_10))
                .thenReturn(Optional.of(completedAchievement));

            // when
            achievementService.updateAchievementProgress(TEST_USER_ID, AchievementType.MISSION_COMPLETE_10, 15);

            // then
            assertThat(completedAchievement.getCurrentCount()).isEqualTo(10); // 변하지 않음
        }
    }

    @Nested
    @DisplayName("incrementAchievementProgress 테스트")
    class IncrementAchievementProgressTest {

        @Test
        @DisplayName("업적 진행도를 1 증가시킨다")
        void incrementAchievementProgress_success() {
            // given
            Achievement achievement = createTestAchievement(1L, AchievementType.FIRST_GUILD_JOIN, 1, 50);
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 0, false);

            when(achievementRepository.findByAchievementType(AchievementType.FIRST_GUILD_JOIN))
                .thenReturn(Optional.of(achievement));
            when(userAchievementRepository.findByUserIdAndAchievementType(TEST_USER_ID, AchievementType.FIRST_GUILD_JOIN))
                .thenReturn(Optional.of(userAchievement));

            // when
            achievementService.incrementAchievementProgress(TEST_USER_ID, AchievementType.FIRST_GUILD_JOIN);

            // then
            assertThat(userAchievement.getCurrentCount()).isEqualTo(1);
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
            Achievement achievement = createTestAchievement(achievementId, AchievementType.FIRST_MISSION_COMPLETE, 1, 50);
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
            Achievement achievement = createTestAchievement(achievementId, AchievementType.FIRST_MISSION_COMPLETE, 1, 50);
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
    @DisplayName("checkMissionAchievements 테스트")
    class CheckMissionAchievementsTest {

        @Test
        @DisplayName("미션 완료 업적을 체크한다")
        void checkMissionAchievements_success() {
            // given
            Achievement achievement = createTestAchievement(1L, AchievementType.FIRST_MISSION_COMPLETE, 1, 50);
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 0, false);

            when(achievementRepository.findByAchievementType(any(AchievementType.class)))
                .thenReturn(Optional.of(achievement));
            when(userAchievementRepository.findByUserIdAndAchievementType(anyString(), any(AchievementType.class)))
                .thenReturn(Optional.of(userAchievement));

            // when
            achievementService.checkMissionAchievements(TEST_USER_ID, 5, false);

            // then
            verify(achievementRepository).findByAchievementType(AchievementType.FIRST_MISSION_COMPLETE);
        }
    }

    @Nested
    @DisplayName("checkLevelAchievements 테스트")
    class CheckLevelAchievementsTest {

        @Test
        @DisplayName("레벨 업적을 체크한다")
        void checkLevelAchievements_success() {
            // given
            Achievement achievement = createTestAchievement(1L, AchievementType.REACH_LEVEL_5, 5, 100);
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 0, false);

            when(achievementRepository.findByAchievementType(any(AchievementType.class)))
                .thenReturn(Optional.of(achievement));
            when(userAchievementRepository.findByUserIdAndAchievementType(anyString(), any(AchievementType.class)))
                .thenReturn(Optional.of(userAchievement));

            // when
            achievementService.checkLevelAchievements(TEST_USER_ID, 10);

            // then
            verify(achievementRepository).findByAchievementType(AchievementType.REACH_LEVEL_5);
        }
    }

    @Nested
    @DisplayName("checkFriendAchievements 테스트")
    class CheckFriendAchievementsTest {

        @Test
        @DisplayName("친구 업적을 체크한다")
        void checkFriendAchievements_success() {
            // given
            Achievement achievement = createTestAchievement(1L, AchievementType.FIRST_FRIEND, 1, 50);
            UserAchievement userAchievement = createTestUserAchievement(1L, TEST_USER_ID, achievement, 0, false);

            when(achievementRepository.findByAchievementType(any(AchievementType.class)))
                .thenReturn(Optional.of(achievement));
            when(userAchievementRepository.findByUserIdAndAchievementType(anyString(), any(AchievementType.class)))
                .thenReturn(Optional.of(userAchievement));

            // when
            achievementService.checkFriendAchievements(TEST_USER_ID, 3);

            // then
            verify(achievementRepository).findByAchievementType(AchievementType.FIRST_FRIEND);
        }
    }
}
