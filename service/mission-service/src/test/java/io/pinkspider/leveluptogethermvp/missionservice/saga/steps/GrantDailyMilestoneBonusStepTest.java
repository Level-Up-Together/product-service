package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.policy.MissionExpPolicy;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GrantDailyMilestoneBonusStep 단위 테스트 (LUT-568)")
class GrantDailyMilestoneBonusStepTest {

    @Mock private GamificationQueryFacade gamificationQueryFacade;

    @InjectMocks private GrantDailyMilestoneBonusStep step;

    private static final String TEST_USER_ID = "test-user-123";

    private MissionCompletionContext context;

    @BeforeEach
    void setUp() {
        context = new MissionCompletionContext(1L, TEST_USER_ID, null);
    }

    @Test
    @DisplayName("Step 이름·재시도 설정")
    void metadata() {
        assertThat(step.getName()).isEqualTo("GrantDailyMilestoneBonus");
        assertThat(step.getMaxRetries()).isEqualTo(2);
        assertThat(step.getRetryDelayMs()).isEqualTo(500L);
        assertThat(step.isMandatory()).isTrue();
    }

    @Nested
    @DisplayName("shouldExecute")
    class ShouldExecuteTest {

        @Test
        @DisplayName("마일스톤 도달 기록이 있을 때만 실행된다")
        void onlyWhenMilestoneMarked() {
            assertThat(step.shouldExecute().test(context)).isFalse();

            context.markDailyMilestone(3);
            assertThat(step.shouldExecute().test(context)).isTrue();
        }

        @Test
        @DisplayName("고정 미션 컨텍스트도 동일하게 판정한다")
        void pinnedContext() {
            MissionCompletionContext pinned =
                    MissionCompletionContext.forPinned(10L, TEST_USER_ID, null, false);
            assertThat(step.shouldExecute().test(pinned)).isFalse();
            pinned.markDailyMilestone(5);
            assertThat(step.shouldExecute().test(pinned)).isTrue();
        }
    }

    @Nested
    @DisplayName("execute")
    class ExecuteTest {

        @Test
        @DisplayName("3개 달성: DAILY_MISSION_MILESTONE / sourceId=3 / 20 EXP / 기본 카테고리로 지급한다")
        void execute_milestone3() {
            context.markDailyMilestone(3);

            SagaStepResult result = step.execute(context);

            assertThat(result.isSuccess()).isTrue();
            verify(gamificationQueryFacade)
                    .addExperience(
                            TEST_USER_ID,
                            MissionExpPolicy.MILESTONE_BONUS_EXP,
                            ExpSourceType.DAILY_MISSION_MILESTONE,
                            3L,
                            "일일 체크 미션 3개 달성 보너스",
                            null,
                            MissionExpPolicy.MILESTONE_CATEGORY_NAME);
        }

        @Test
        @DisplayName("5개 달성: sourceId=5 로 지급한다")
        void execute_milestone5() {
            context.markDailyMilestone(5);

            SagaStepResult result = step.execute(context);

            assertThat(result.isSuccess()).isTrue();
            verify(gamificationQueryFacade)
                    .addExperience(
                            TEST_USER_ID,
                            MissionExpPolicy.MILESTONE_BONUS_EXP,
                            ExpSourceType.DAILY_MISSION_MILESTONE,
                            5L,
                            "일일 체크 미션 5개 달성 보너스",
                            null,
                            MissionExpPolicy.MILESTONE_CATEGORY_NAME);
        }

        @Test
        @DisplayName("지급 실패 시 failure 를 반환한다 (필수 Step 이라 Saga 보상으로 이어진다)")
        void execute_failure() {
            context.markDailyMilestone(3);
            doThrow(new RuntimeException("DB 오류"))
                    .when(gamificationQueryFacade)
                    .addExperience(
                            anyString(),
                            anyInt(),
                            any(ExpSourceType.class),
                            anyLong(),
                            anyString(),
                            isNull(),
                            anyString());

            SagaStepResult result = step.execute(context);

            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("compensate")
    class CompensateTest {

        @Test
        @DisplayName("같은 출처·sourceId 로 보너스를 환수한다")
        void compensate_success() {
            context.markDailyMilestone(3);

            SagaStepResult result = step.compensate(context);

            assertThat(result.isSuccess()).isTrue();
            verify(gamificationQueryFacade)
                    .subtractExperience(
                            TEST_USER_ID,
                            MissionExpPolicy.MILESTONE_BONUS_EXP,
                            ExpSourceType.DAILY_MISSION_MILESTONE,
                            3L,
                            "일일 체크 미션 마일스톤 보너스 환수",
                            null,
                            MissionExpPolicy.MILESTONE_CATEGORY_NAME);
        }

        @Test
        @DisplayName("환수 실패 시 failure 를 반환한다")
        void compensate_failure() {
            context.markDailyMilestone(3);
            doThrow(new RuntimeException("DB 오류"))
                    .when(gamificationQueryFacade)
                    .subtractExperience(
                            anyString(),
                            anyInt(),
                            any(ExpSourceType.class),
                            anyLong(),
                            anyString(),
                            isNull(),
                            anyString());

            SagaStepResult result = step.compensate(context);

            assertThat(result.isSuccess()).isFalse();
        }
    }
}
