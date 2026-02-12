package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserCategoryExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserCategoryExperienceRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserCategoryExperienceCheckStrategyTest {

    @Mock
    private UserCategoryExperienceRepository userCategoryExperienceRepository;

    @InjectMocks
    private UserCategoryExperienceCheckStrategy strategy;

    private static final String TEST_USER_ID = "test-user-123";

    private Achievement createTestAchievement(Long id, String dataField, String operator, int requiredCount) {
        Achievement achievement = Achievement.builder()
            .name("테스트 업적")
            .checkLogicDataSource("USER_CATEGORY_EXPERIENCE")
            .checkLogicDataField(dataField)
            .comparisonOperator(operator)
            .requiredCount(requiredCount)
            .build();
        setId(achievement, id);
        return achievement;
    }

    private UserCategoryExperience createCategoryExperience(Long categoryId, Long totalExp) {
        return UserCategoryExperience.builder()
            .userId(TEST_USER_ID)
            .categoryId(categoryId)
            .categoryName("테스트 카테고리")
            .totalExp(totalExp)
            .build();
    }

    @Nested
    @DisplayName("getDataSource 테스트")
    class GetDataSourceTest {

        @Test
        @DisplayName("데이터 소스를 반환한다")
        void getDataSource_returnsUserCategoryExperience() {
            // when
            String result = strategy.getDataSource();

            // then
            assertThat(result).isEqualTo("USER_CATEGORY_EXPERIENCE");
        }
    }

    @Nested
    @DisplayName("fetchCurrentValue 테스트")
    class FetchCurrentValueTest {

        @Test
        @DisplayName("특정 카테고리의 누적 경험치를 반환한다")
        void fetchCurrentValue_categoryField() {
            // given
            UserCategoryExperience categoryExp = createCategoryExperience(1L, 500L);
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(categoryExp));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "category_1");

            // then
            assertThat(result).isEqualTo(500L);
        }

        @Test
        @DisplayName("해당 카테고리 경험치가 없으면 0을 반환한다")
        void fetchCurrentValue_noCategoryExp_returnsZero() {
            // given
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                .thenReturn(Optional.empty());

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "category_1");

            // then
            assertThat(result).isEqualTo(0L);
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
            Achievement achievement = createTestAchievement(1L, "category_1", "GTE", 500);
            UserCategoryExperience categoryExp = createCategoryExperience(1L, 1000L);
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(categoryExp));

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("조건이 정확히 일치해도 GTE는 true를 반환한다")
        void checkCondition_equalValue_returnsTrue() {
            // given
            Achievement achievement = createTestAchievement(1L, "category_1", "GTE", 500);
            UserCategoryExperience categoryExp = createCategoryExperience(1L, 500L);
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(categoryExp));

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("조건을 만족하지 않으면 false를 반환한다")
        void checkCondition_notSatisfied_returnsFalse() {
            // given
            Achievement achievement = createTestAchievement(1L, "category_1", "GTE", 1000);
            UserCategoryExperience categoryExp = createCategoryExperience(1L, 500L);
            when(userCategoryExperienceRepository.findByUserIdAndCategoryId(TEST_USER_ID, 1L))
                .thenReturn(Optional.of(categoryExp));

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
