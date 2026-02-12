package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
class FeedServiceCheckStrategyTest {

    @Mock
    private UserStatsRepository userStatsRepository;

    @InjectMocks
    private FeedServiceCheckStrategy strategy;

    private static final String TEST_USER_ID = "test-user-123";

    private Achievement createTestAchievement(Long id, String dataField, String operator, int requiredCount) {
        Achievement achievement = Achievement.builder()
            .name("테스트 업적")
            .checkLogicDataSource("FEED_SERVICE")
            .checkLogicDataField(dataField)
            .comparisonOperator(operator)
            .requiredCount(requiredCount)
            .build();
        setId(achievement, id);
        return achievement;
    }

    private UserStats createTestUserStats(Long totalLikesReceived) {
        return UserStats.builder()
            .userId(TEST_USER_ID)
            .totalLikesReceived(totalLikesReceived)
            .build();
    }

    @Nested
    @DisplayName("getDataSource 테스트")
    class GetDataSourceTest {

        @Test
        @DisplayName("데이터 소스를 반환한다")
        void getDataSource_returnsFeedService() {
            // when
            String result = strategy.getDataSource();

            // then
            assertThat(result).isEqualTo("FEED_SERVICE");
        }
    }

    @Nested
    @DisplayName("fetchCurrentValue 테스트")
    class FetchCurrentValueTest {

        @Test
        @DisplayName("totalLikesReceived 필드 값을 반환한다")
        void fetchCurrentValue_totalLikesReceived() {
            // given
            UserStats stats = createTestUserStats(50L);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "totalLikesReceived");

            // then
            assertThat(result).isEqualTo(50L);
        }

        @Test
        @DisplayName("UserStats가 없으면 0을 반환한다")
        void fetchCurrentValue_noStats_returnsZero() {
            // given
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "totalLikesReceived");

            // then
            assertThat(result).isEqualTo(0L);
        }

        @Test
        @DisplayName("알 수 없는 필드면 0을 반환한다")
        void fetchCurrentValue_unknownField_returnsZero() {
            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "unknownField");

            // then
            assertThat(result).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("checkCondition 테스트")
    class CheckConditionTest {

        @Test
        @DisplayName("조건을 만족하면 true를 반환한다")
        void checkCondition_satisfied_returnsTrue() {
            // given
            Achievement achievement = createTestAchievement(1L, "totalLikesReceived", "GTE", 10);
            UserStats stats = createTestUserStats(50L);
            when(userStatsRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(stats));

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("조건을 만족하지 않으면 false를 반환한다")
        void checkCondition_notSatisfied_returnsFalse() {
            // given
            Achievement achievement = createTestAchievement(1L, "totalLikesReceived", "GTE", 100);
            UserStats stats = createTestUserStats(50L);
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
