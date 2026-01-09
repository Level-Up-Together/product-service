package io.pinkspider.global.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaOrchestrator 단위 테스트")
class SagaOrchestratorTest {

    @Mock
    private SagaEventPublisher eventPublisher;

    private SagaOrchestrator<TestContext> orchestrator;
    private TestContext context;

    @BeforeEach
    void setUp() {
        orchestrator = new SagaOrchestrator<>(eventPublisher);
        context = new TestContext("test-user");
    }

    @Nested
    @DisplayName("addStep 테스트")
    class AddStepTest {

        @Test
        @DisplayName("Step을 추가하고 체이닝을 지원한다")
        void addStep_supportsChaining() {
            // given
            SagaStep<TestContext> step1 = createMockStep("Step1", true);
            SagaStep<TestContext> step2 = createMockStep("Step2", true);

            // when
            SagaOrchestrator<TestContext> result = orchestrator
                .addStep(step1)
                .addStep(step2);

            // then
            assertThat(result).isSameAs(orchestrator);
        }
    }

    @Nested
    @DisplayName("execute 테스트")
    class ExecuteTest {

        @Test
        @DisplayName("모든 Step이 성공하면 Saga가 성공한다")
        void execute_allStepsSuccess() {
            // given
            SagaStep<TestContext> step1 = createMockStep("Step1", true);
            SagaStep<TestContext> step2 = createMockStep("Step2", true);

            orchestrator.addStep(step1).addStep(step2);

            // when
            SagaResult<TestContext> result = orchestrator.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            verify(eventPublisher).publishSagaCompleted(context);
        }

        @Test
        @DisplayName("필수 Step 실패 시 보상 트랜잭션을 실행한다")
        void execute_mandatoryStepFails_compensates() {
            // given
            SagaStep<TestContext> step1 = createMockStep("Step1", true);
            SagaStep<TestContext> step2 = createMockStep("Step2", false, true); // fails, mandatory

            orchestrator.addStep(step1).addStep(step2);

            // when
            SagaResult<TestContext> result = orchestrator.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            verify(step1).compensate(context);
            verify(eventPublisher).publishSagaCompensated(context);
        }

        @Test
        @DisplayName("비필수 Step 실패 시 계속 진행한다")
        void execute_nonMandatoryStepFails_continues() {
            // given
            SagaStep<TestContext> step1 = createMockStep("Step1", true);
            SagaStep<TestContext> step2 = createMockStep("Step2", false, false); // fails, not mandatory
            SagaStep<TestContext> step3 = createMockStep("Step3", true);

            orchestrator.addStep(step1).addStep(step2).addStep(step3);

            // when
            SagaResult<TestContext> result = orchestrator.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(step3).execute(context);
            verify(step1, never()).compensate(context);
        }

        @Test
        @DisplayName("조건을 만족하지 않는 Step은 스킵한다")
        void execute_conditionNotMet_skipsStep() {
            // given
            SagaStep<TestContext> step1 = createMockStep("Step1", true);
            SagaStep<TestContext> step2 = createConditionalMockStep("Step2", false);
            SagaStep<TestContext> step3 = createMockStep("Step3", true);

            orchestrator.addStep(step1).addStep(step2).addStep(step3);

            // when
            SagaResult<TestContext> result = orchestrator.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(step2, never()).execute(context);
            verify(step3).execute(context);
        }

        @Test
        @DisplayName("Step 실패 시 재시도를 수행한다")
        void execute_retryOnFailure() {
            // given
            SagaStep<TestContext> step1 = mock(SagaStep.class);
            when(step1.getName()).thenReturn("RetryStep");
            when(step1.shouldExecute()).thenReturn(ctx -> true);
            when(step1.getMaxRetries()).thenReturn(2);
            when(step1.getRetryDelayMs()).thenReturn(10L);
            when(step1.isMandatory()).thenReturn(true);
            when(step1.execute(any()))
                .thenReturn(SagaStepResult.failure("첫 번째 시도 실패"))
                .thenReturn(SagaStepResult.failure("두 번째 시도 실패"))
                .thenReturn(SagaStepResult.success("성공"));

            orchestrator.addStep(step1);

            // when
            SagaResult<TestContext> result = orchestrator.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(step1, times(3)).execute(context);
        }
    }

    @Nested
    @DisplayName("getExecutionLogs 테스트")
    class GetExecutionLogsTest {

        @Test
        @DisplayName("실행 로그를 반환한다")
        void getExecutionLogs_returnsLogs() {
            // given
            SagaStep<TestContext> step = createMockStep("Step1", true);
            orchestrator.addStep(step);

            // when
            orchestrator.execute(context);

            // then
            assertThat(orchestrator.getExecutionLogs()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("생성자 테스트")
    class ConstructorTest {

        @Test
        @DisplayName("EventPublisher 없이 생성할 수 있다")
        void constructor_withoutEventPublisher() {
            // given
            SagaOrchestrator<TestContext> noPublisherOrchestrator = new SagaOrchestrator<>();
            SagaStep<TestContext> step = createMockStep("Step1", true);
            noPublisherOrchestrator.addStep(step);

            // when
            SagaResult<TestContext> result = noPublisherOrchestrator.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
        }
    }

    private SagaStep<TestContext> createMockStep(String name, boolean success) {
        return createMockStep(name, success, true);
    }

    private SagaStep<TestContext> createMockStep(String name, boolean success, boolean mandatory) {
        SagaStep<TestContext> step = mock(SagaStep.class);
        when(step.getName()).thenReturn(name);
        when(step.shouldExecute()).thenReturn(ctx -> true);
        when(step.getMaxRetries()).thenReturn(0);
        when(step.getRetryDelayMs()).thenReturn(0L);
        when(step.isMandatory()).thenReturn(mandatory);

        if (success) {
            when(step.execute(any())).thenReturn(SagaStepResult.success());
        } else {
            when(step.execute(any())).thenReturn(SagaStepResult.failure("Step failed"));
        }

        when(step.compensate(any())).thenReturn(SagaStepResult.success());

        return step;
    }

    private SagaStep<TestContext> createConditionalMockStep(String name, boolean shouldExecute) {
        SagaStep<TestContext> step = mock(SagaStep.class);
        when(step.getName()).thenReturn(name);
        when(step.shouldExecute()).thenReturn(ctx -> shouldExecute);
        when(step.getMaxRetries()).thenReturn(0);
        when(step.getRetryDelayMs()).thenReturn(0L);
        when(step.isMandatory()).thenReturn(true);
        when(step.execute(any())).thenReturn(SagaStepResult.success());
        when(step.compensate(any())).thenReturn(SagaStepResult.success());
        return step;
    }

    /**
     * 테스트용 Saga Context
     */
    static class TestContext extends SagaContext {
        public static final String SAGA_TYPE = "TEST_SAGA";

        public TestContext(String executorId) {
            super(SAGA_TYPE, executorId);
        }
    }
}
