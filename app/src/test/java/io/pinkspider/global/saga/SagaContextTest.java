package io.pinkspider.global.saga;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SagaContext 단위 테스트")
class SagaContextTest {

    private TestSagaContext context;

    @BeforeEach
    void setUp() {
        context = new TestSagaContext("test-user");
    }

    @Nested
    @DisplayName("생성자 테스트")
    class ConstructorTest {

        @Test
        @DisplayName("sagaType만으로 생성할 수 있다")
        void constructor_withSagaTypeOnly() {
            // when
            TestSagaContext ctx = new TestSagaContext();

            // then
            assertThat(ctx.getSagaId()).isNotNull();
            assertThat(ctx.getSagaType()).isEqualTo("TEST_SAGA");
            assertThat(ctx.getStatus()).isEqualTo(SagaStatus.STARTED);
            assertThat(ctx.getStartedAt()).isNotNull();
        }

        @Test
        @DisplayName("sagaType과 executorId로 생성할 수 있다")
        void constructor_withExecutorId() {
            // then
            assertThat(context.getSagaId()).isNotNull();
            assertThat(context.getSagaType()).isEqualTo("TEST_SAGA");
            assertThat(context.getExecutorId()).isEqualTo("test-user");
            assertThat(context.getStatus()).isEqualTo(SagaStatus.STARTED);
        }
    }

    @Nested
    @DisplayName("stepResults 테스트")
    class StepResultsTest {

        @Test
        @DisplayName("Step 결과를 저장하고 조회할 수 있다")
        void addAndGetStepResult() {
            // given
            String stepName = "TestStep";
            SagaStepResult result = SagaStepResult.success("완료");

            // when
            context.addStepResult(stepName, result);
            SagaStepResult retrieved = context.getStepResult(stepName);

            // then
            assertThat(retrieved).isEqualTo(result);
            assertThat(retrieved.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 Step 결과는 null을 반환한다")
        void getStepResult_notExists_returnsNull() {
            // when
            SagaStepResult result = context.getStepResult("NonExistentStep");

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("compensationData 테스트")
    class CompensationDataTest {

        @Test
        @DisplayName("보상 데이터를 저장하고 조회할 수 있다")
        void addAndGetCompensationData() {
            // given
            String key = "previousBalance";
            Integer value = 1000;

            // when
            context.addCompensationData(key, value);
            Integer retrieved = context.getCompensationData(key, Integer.class);

            // then
            assertThat(retrieved).isEqualTo(value);
        }

        @Test
        @DisplayName("다양한 타입의 보상 데이터를 저장할 수 있다")
        void addCompensationData_variousTypes() {
            // given & when
            context.addCompensationData("intValue", 100);
            context.addCompensationData("stringValue", "test");
            context.addCompensationData("boolValue", true);

            // then
            assertThat(context.getCompensationData("intValue", Integer.class)).isEqualTo(100);
            assertThat(context.getCompensationData("stringValue", String.class)).isEqualTo("test");
            assertThat(context.getCompensationData("boolValue", Boolean.class)).isEqualTo(true);
        }

        @Test
        @DisplayName("존재하지 않는 키는 null을 반환한다")
        void getCompensationData_notExists_returnsNull() {
            // when
            Object result = context.getCompensationData("nonexistent", Object.class);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("상태 변경 테스트")
    class StatusChangeTest {

        @Test
        @DisplayName("완료 상태로 변경할 수 있다")
        void complete_changesStatus() {
            // when
            context.complete();

            // then
            assertThat(context.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(context.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("실패 상태로 변경할 수 있다")
        void fail_changesStatus() {
            // given
            String reason = "DB 연결 실패";
            RuntimeException exception = new RuntimeException("Connection refused");

            // when
            context.fail(reason, exception);

            // then
            assertThat(context.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(context.getFailureReason()).isEqualTo(reason);
            assertThat(context.getFailureException()).isEqualTo(exception);
            assertThat(context.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("보상 시작 상태로 변경할 수 있다")
        void startCompensation_changesStatus() {
            // when
            context.startCompensation();

            // then
            assertThat(context.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
        }

        @Test
        @DisplayName("보상 완료 상태로 변경할 수 있다")
        void compensated_changesStatus() {
            // when
            context.compensated();

            // then
            assertThat(context.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
            assertThat(context.getCompletedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("상태 전이 시나리오 테스트")
    class StatusTransitionScenarioTest {

        @Test
        @DisplayName("성공 시나리오: STARTED -> PROCESSING -> COMPLETED")
        void successScenario() {
            // then
            assertThat(context.getStatus()).isEqualTo(SagaStatus.STARTED);

            // when
            context.setStatus(SagaStatus.PROCESSING);

            // then
            assertThat(context.getStatus()).isEqualTo(SagaStatus.PROCESSING);

            // when
            context.complete();

            // then
            assertThat(context.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        }

        @Test
        @DisplayName("실패 및 보상 시나리오: STARTED -> PROCESSING -> FAILED -> COMPENSATING -> COMPENSATED")
        void failureAndCompensationScenario() {
            // Initial state
            assertThat(context.getStatus()).isEqualTo(SagaStatus.STARTED);

            // Processing
            context.setStatus(SagaStatus.PROCESSING);
            assertThat(context.getStatus()).isEqualTo(SagaStatus.PROCESSING);

            // Fail
            context.fail("Error", new RuntimeException());
            assertThat(context.getStatus()).isEqualTo(SagaStatus.FAILED);

            // Start compensation
            context.startCompensation();
            assertThat(context.getStatus()).isEqualTo(SagaStatus.COMPENSATING);

            // Complete compensation
            context.compensated();
            assertThat(context.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        }
    }

    /**
     * 테스트용 Saga Context 구현
     */
    static class TestSagaContext extends SagaContext {
        public static final String SAGA_TYPE = "TEST_SAGA";

        public TestSagaContext() {
            super(SAGA_TYPE);
        }

        public TestSagaContext(String executorId) {
            super(SAGA_TYPE, executorId);
        }
    }
}
