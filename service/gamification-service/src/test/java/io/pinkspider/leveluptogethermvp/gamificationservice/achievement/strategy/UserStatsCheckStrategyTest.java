package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserStatsCheckStrategyTest {

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private GuildQueryFacade guildQueryFacade;

    @InjectMocks
    private UserStatsCheckStrategy strategy;

    private static final String TEST_USER_ID = "test-user-123";

    private UserStats createTestUserStats(int totalMissionCompletions, int currentStreak, int maxStreak) {
        return UserStats.builder()
            .userId(TEST_USER_ID)
            .totalMissionCompletions(totalMissionCompletions)
            .totalMissionFullCompletions(5)
            .totalGuildMissionCompletions(3)
            .currentStreak(currentStreak)
            .maxStreak(maxStreak)
            .totalAchievementsCompleted(2)
            .totalTitlesAcquired(4)
            .maxCompletedMissionDuration(30)
            .build();
    }

    private Achievement createTestAchievement(Long id, String dataField, String operator, int requiredCount) {
        Achievement achievement = Achievement.builder()
            .name("테스트 업적")
            .checkLogicDataSource("USER_STATS")
            .checkLogicDataField(dataField)
            .comparisonOperator(operator)
            .requiredCount(requiredCount)
            .build();
        setId(achievement, id);
        return achievement;
    }

    @Nested
    @DisplayName("getDataSource 테스트")
    class GetDataSourceTest {

        @Test
        @DisplayName("데이터 소스를 반환한다")
        void getDataSource_returnsUserStats() {
            // when
            String result = strategy.getDataSource();

            // then
            assertThat(result).isEqualTo("USER_STATS");
        }
    }

    @Nested
    @DisplayName("fetchCurrentValue 테스트")
    class FetchCurrentValueTest {

        @Test
        @DisplayName("totalMissionCompletions 필드 값을 반환한다")
        void fetchCurrentValue_totalMissionCompletions() {
            // given
            UserStats stats = createTestUserStats(10, 5, 7);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "totalMissionCompletions");

            // then
            assertThat(result).isEqualTo(10);
        }

        @Test
        @DisplayName("totalMissionFullCompletions 필드 값을 반환한다")
        void fetchCurrentValue_totalMissionFullCompletions() {
            // given
            UserStats stats = createTestUserStats(10, 5, 7);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "totalMissionFullCompletions");

            // then
            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("currentStreak 필드 값을 반환한다")
        void fetchCurrentValue_currentStreak() {
            // given
            UserStats stats = createTestUserStats(10, 5, 7);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "currentStreak");

            // then
            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("maxStreak 필드 값을 반환한다")
        void fetchCurrentValue_maxStreak() {
            // given
            UserStats stats = createTestUserStats(10, 5, 7);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "maxStreak");

            // then
            assertThat(result).isEqualTo(7);
        }

        @Test
        @DisplayName("totalGuildMissionCompletions 필드 값을 반환한다")
        void fetchCurrentValue_totalGuildMissionCompletions() {
            // given
            UserStats stats = createTestUserStats(10, 5, 7);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "totalGuildMissionCompletions");

            // then
            assertThat(result).isEqualTo(3);
        }

        @Test
        @DisplayName("totalAchievementsCompleted 필드 값을 반환한다")
        void fetchCurrentValue_totalAchievementsCompleted() {
            // given
            UserStats stats = createTestUserStats(10, 5, 7);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "totalAchievementsCompleted");

            // then
            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("totalTitlesAcquired 필드 값을 반환한다")
        void fetchCurrentValue_totalTitlesAcquired() {
            // given
            UserStats stats = createTestUserStats(10, 5, 7);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "totalTitlesAcquired");

            // then
            assertThat(result).isEqualTo(4);
        }

        @Test
        @DisplayName("maxCompletedMissionDuration 필드 값을 반환한다")
        void fetchCurrentValue_maxCompletedMissionDuration() {
            // given
            UserStats stats = createTestUserStats(10, 5, 7);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "maxCompletedMissionDuration");

            // then
            assertThat(result).isEqualTo(30);
        }

        @Test
        @DisplayName("guildJoinCount 필드 값을 반환한다")
        void fetchCurrentValue_guildJoinCount() {
            // given
            UserStats stats = UserStats.builder()
                .userId(TEST_USER_ID)
                .guildJoinCount(3)
                .build();
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "guildJoinCount");

            // then
            assertThat(result).isEqualTo(3);
        }

        @Test
        @DisplayName("friendCount alias 값을 반환한다")
        void fetchCurrentValue_friendCount() {
            UserStats stats = UserStats.builder().userId(TEST_USER_ID).friendCount(11).build();
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            assertThat(strategy.fetchCurrentValue(TEST_USER_ID, "friendCount")).isEqualTo(11);
        }

        @Test
        @DisplayName("receivedLikeCount alias는 totalLikesReceived 값을 반환한다")
        void fetchCurrentValue_receivedLikeCount_alias() {
            UserStats stats = UserStats.builder().userId(TEST_USER_ID).totalLikesReceived(42L).build();
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            assertThat(strategy.fetchCurrentValue(TEST_USER_ID, "receivedLikeCount")).isEqualTo(42L);
            assertThat(strategy.fetchCurrentValue(TEST_USER_ID, "totalLikesReceived")).isEqualTo(42L);
        }

        @Test
        @DisplayName("maxStreakDays alias는 maxStreak 값을 반환한다")
        void fetchCurrentValue_maxStreakDays_alias() {
            UserStats stats = createTestUserStats(0, 0, 9);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            assertThat(strategy.fetchCurrentValue(TEST_USER_ID, "maxStreakDays")).isEqualTo(9);
        }

        @Test
        @DisplayName("guildMissionCount alias는 totalGuildMissionCompletions 값을 반환한다")
        void fetchCurrentValue_guildMissionCount_alias() {
            UserStats stats = createTestUserStats(0, 0, 0);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            assertThat(strategy.fetchCurrentValue(TEST_USER_ID, "guildMissionCount")).isEqualTo(3);
        }

        @Test
        @DisplayName("isGuildMaster: 길드 마스터인 멤버십이 있으면 1을 반환한다")
        void fetchCurrentValue_isGuildMaster_true() {
            io.pinkspider.global.facade.dto.GuildMembershipInfo m1 =
                new io.pinkspider.global.facade.dto.GuildMembershipInfo(1L, "g1", null, 1, true, false);
            when(guildQueryFacade.getUserGuildMemberships(TEST_USER_ID)).thenReturn(java.util.List.of(m1));

            assertThat(strategy.fetchCurrentValue(TEST_USER_ID, "isGuildMaster")).isEqualTo(1);
        }

        @Test
        @DisplayName("isGuildMaster: 마스터 멤버십이 없으면 0을 반환한다")
        void fetchCurrentValue_isGuildMaster_false() {
            io.pinkspider.global.facade.dto.GuildMembershipInfo m1 =
                new io.pinkspider.global.facade.dto.GuildMembershipInfo(1L, "g1", null, 1, false, true);
            when(guildQueryFacade.getUserGuildMemberships(TEST_USER_ID)).thenReturn(java.util.List.of(m1));

            assertThat(strategy.fetchCurrentValue(TEST_USER_ID, "isGuildMaster")).isEqualTo(0);
        }

        @Test
        @DisplayName("알 수 없는 필드면 0을 반환한다")
        void fetchCurrentValue_unknownField_returnsZero() {
            // given
            UserStats stats = createTestUserStats(10, 5, 7);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "unknownField");

            // then
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("사용자 통계가 없으면 0을 반환한다")
        void fetchCurrentValue_noStats_returnsZero() {
            // given
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "totalMissionCompletions");

            // then
            assertThat(result).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("checkCondition 테스트")
    class CheckConditionTest {

        @Test
        @DisplayName("조건을 만족하면 true를 반환한다 (GTE)")
        void checkCondition_gte_satisfied_returnsTrue() {
            // given
            UserStats stats = createTestUserStats(10, 5, 7);
            Achievement achievement = createTestAchievement(1L, "totalMissionCompletions", "GTE", 10);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("조건을 만족하지 않으면 false를 반환한다 (GTE)")
        void checkCondition_gte_notSatisfied_returnsFalse() {
            // given
            UserStats stats = createTestUserStats(5, 5, 7);
            Achievement achievement = createTestAchievement(1L, "totalMissionCompletions", "GTE", 10);

            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("dataField가 null이면 false를 반환한다")
        void checkCondition_nullDataField_returnsFalse() {
            // given
            Achievement achievement = createTestAchievement(1L, null, "GTE", 10);

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isFalse();
        }
    }
}
