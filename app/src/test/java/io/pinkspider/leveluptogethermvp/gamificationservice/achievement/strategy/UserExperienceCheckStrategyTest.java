package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserExperienceCheckStrategyTest {

    @Mock
    private UserExperienceRepository userExperienceRepository;

    @InjectMocks
    private UserExperienceCheckStrategy strategy;

    private static final String TEST_USER_ID = "test-user-123";

    private UserExperience createTestUserExperience(int currentLevel, int currentExp, int totalExp) {
        return UserExperience.builder()
            .userId(TEST_USER_ID)
            .currentLevel(currentLevel)
            .currentExp(currentExp)
            .totalExp(totalExp)
            .build();
    }

    private Achievement createTestAchievement(Long id, String dataField, String operator, int requiredCount) {
        Achievement achievement = Achievement.builder()
            .name("테스트 업적")
            .checkLogicDataSource("USER_EXPERIENCE")
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
        void getDataSource_returnsUserExperience() {
            // when
            String result = strategy.getDataSource();

            // then
            assertThat(result).isEqualTo("USER_EXPERIENCE");
        }
    }

    @Nested
    @DisplayName("fetchCurrentValue 테스트")
    class FetchCurrentValueTest {

        @Test
        @DisplayName("currentLevel 필드 값을 반환한다")
        void fetchCurrentValue_currentLevel() {
            // given
            UserExperience userExp = createTestUserExperience(15, 500, 10000);
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "currentLevel");

            // then
            assertThat(result).isEqualTo(15);
        }

        @Test
        @DisplayName("totalExp 필드 값을 반환한다")
        void fetchCurrentValue_totalExp() {
            // given
            UserExperience userExp = createTestUserExperience(15, 500, 10000);
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "totalExp");

            // then
            assertThat(result).isEqualTo(10000);
        }

        @Test
        @DisplayName("currentExp 필드 값을 반환한다")
        void fetchCurrentValue_currentExp() {
            // given
            UserExperience userExp = createTestUserExperience(15, 500, 10000);
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "currentExp");

            // then
            assertThat(result).isEqualTo(500);
        }

        @Test
        @DisplayName("알 수 없는 필드면 0을 반환한다")
        void fetchCurrentValue_unknownField_returnsZero() {
            // given
            UserExperience userExp = createTestUserExperience(15, 500, 10000);
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "unknownField");

            // then
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("사용자 경험치가 없으면 0을 반환한다")
        void fetchCurrentValue_noUserExperience_returnsZero() {
            // given
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            // when
            Object result = strategy.fetchCurrentValue(TEST_USER_ID, "currentLevel");

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
            UserExperience userExp = createTestUserExperience(15, 500, 10000);
            Achievement achievement = createTestAchievement(1L, "currentLevel", "GTE", 10);

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("조건을 만족하지 않으면 false를 반환한다 (GTE)")
        void checkCondition_gte_notSatisfied_returnsFalse() {
            // given
            UserExperience userExp = createTestUserExperience(5, 500, 1000);
            Achievement achievement = createTestAchievement(1L, "currentLevel", "GTE", 10);

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("totalExp 조건을 만족하면 true를 반환한다")
        void checkCondition_totalExp_satisfied_returnsTrue() {
            // given
            UserExperience userExp = createTestUserExperience(15, 500, 50000);
            Achievement achievement = createTestAchievement(1L, "totalExp", "GTE", 10000);

            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(userExp));

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isTrue();
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

        @Test
        @DisplayName("사용자 경험치가 없으면 false를 반환한다")
        void checkCondition_noUserExperience_returnsFalse() {
            // given
            Achievement achievement = createTestAchievement(1L, "currentLevel", "GTE", 10);
            when(userExperienceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            // when
            boolean result = strategy.checkCondition(TEST_USER_ID, achievement);

            // then
            assertThat(result).isFalse();
        }
    }
}
