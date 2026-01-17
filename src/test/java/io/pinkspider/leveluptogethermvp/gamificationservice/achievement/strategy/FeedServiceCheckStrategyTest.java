package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.FeedLikeRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import java.lang.reflect.Field;
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
    private FeedLikeRepository feedLikeRepository;

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
        try {
            Field idField = Achievement.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(achievement, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return achievement;
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
            when(feedLikeRepository.countLikesReceivedByUser(TEST_USER_ID)).thenReturn(50L);

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "totalLikesReceived");

            // then
            assertThat(result).isEqualTo(50L);
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
            when(feedLikeRepository.countLikesReceivedByUser(TEST_USER_ID)).thenReturn(50L);

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
            when(feedLikeRepository.countLikesReceivedByUser(TEST_USER_ID)).thenReturn(50L);

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
