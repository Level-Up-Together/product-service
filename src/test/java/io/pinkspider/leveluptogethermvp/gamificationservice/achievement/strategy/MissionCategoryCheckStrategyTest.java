package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionCategoryCheckStrategyTest {

    @Mock
    private MissionExecutionRepository missionExecutionRepository;

    @InjectMocks
    private MissionCategoryCheckStrategy strategy;

    private static final String TEST_USER_ID = "test-user-123";

    private Achievement createTestAchievement(Long id, String dataField, String operator, int requiredCount) {
        Achievement achievement = Achievement.builder()
            .name("테스트 업적")
            .checkLogicDataSource("MISSION_CATEGORY")
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
        void getDataSource_returnsMissionCategory() {
            // when
            String result = strategy.getDataSource();

            // then
            assertThat(result).isEqualTo("MISSION_CATEGORY");
        }
    }

    @Nested
    @DisplayName("fetchCurrentValue 테스트")
    class FetchCurrentValueTest {

        @Test
        @DisplayName("특정 카테고리의 미션 완료 횟수를 반환한다")
        void fetchCurrentValue_categoryField() {
            // given
            when(missionExecutionRepository.countCompletedByUserIdAndCategoryId(TEST_USER_ID, 1L))
                .thenReturn(10L);

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "category_1");

            // then
            assertThat(result).isEqualTo(10L);
        }

        @Test
        @DisplayName("잘못된 형식의 dataField면 0을 반환한다")
        void fetchCurrentValue_invalidFormat_returnsZero() {
            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "invalid_format");

            // then
            assertThat(result).isEqualTo(0L);
        }

        @Test
        @DisplayName("숫자가 아닌 카테고리 ID면 0을 반환한다")
        void fetchCurrentValue_nonNumericCategoryId_returnsZero() {
            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "category_abc");

            // then
            assertThat(result).isEqualTo(0L);
        }

        @Test
        @DisplayName("null dataField면 0을 반환한다")
        void fetchCurrentValue_nullDataField_returnsZero() {
            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, null);

            // then
            assertThat(result).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("checkCondition 테스트")
    class CheckConditionTest {

        @Test
        @DisplayName("조건을 만족하면 true를 반환한다")
        void checkCondition_satisfied_returnsTrue() {
            // given
            Achievement achievement = createTestAchievement(1L, "category_1", "GTE", 5);
            when(missionExecutionRepository.countCompletedByUserIdAndCategoryId(TEST_USER_ID, 1L))
                .thenReturn(10L);

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("조건을 만족하지 않으면 false를 반환한다")
        void checkCondition_notSatisfied_returnsFalse() {
            // given
            Achievement achievement = createTestAchievement(1L, "category_1", "GTE", 20);
            when(missionExecutionRepository.countCompletedByUserIdAndCategoryId(TEST_USER_ID, 1L))
                .thenReturn(10L);

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
