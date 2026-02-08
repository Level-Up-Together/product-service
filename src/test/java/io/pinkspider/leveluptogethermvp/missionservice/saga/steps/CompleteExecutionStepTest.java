package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompleteExecutionStep 단위 테스트")
class CompleteExecutionStepTest {

    @Mock
    private MissionExecutionRepository executionRepository;

    @InjectMocks
    private CompleteExecutionStep completeExecutionStep;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long EXECUTION_ID = 1L;

    private Mission mission;
    private MissionParticipant participant;
    private MissionExecution execution;
    private MissionCompletionContext context;

    @BeforeEach
    void setUp() {
        mission = Mission.builder()
            .title("30일 운동 챌린지")
            .description("매일 운동하기")
            .creatorId(TEST_USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PUBLIC)
            .type(MissionType.PERSONAL)
            .expPerCompletion(50)
            .build();
        setId(mission, 1L);

        participant = MissionParticipant.builder()
            .mission(mission)
            .userId(TEST_USER_ID)
            .status(ParticipantStatus.IN_PROGRESS)
            .progress(5)
            .build();
        setId(participant, 1L);

        execution = MissionExecution.builder()
            .participant(participant)
            .executionDate(LocalDate.now())
            .status(ExecutionStatus.IN_PROGRESS)
            .build();
        setId(execution, EXECUTION_ID);
        // 시작 시간 설정 (complete() 메서드에서 필요)
        execution.setStartedAt(LocalDateTime.now().minusMinutes(30));

        context = new MissionCompletionContext(EXECUTION_ID, TEST_USER_ID, "완료 메모");
        context.setExecution(execution);
        context.setParticipant(participant);
        context.setMission(mission);
        context.addCompensationData(
            MissionCompletionContext.CompensationKeys.EXECUTION_STATUS_BEFORE,
            ExecutionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Step 이름이 'CompleteExecution'이다")
    void getName_returnsCorrectName() {
        assertThat(completeExecutionStep.getName()).isEqualTo("CompleteExecution");
    }

    @Nested
    @DisplayName("execute 테스트")
    class ExecuteTest {

        @Test
        @DisplayName("정상적으로 수행을 완료 처리한다")
        void execute_success() {
            // given
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completeExecutionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(execution.getCompletedAt()).isNotNull();
            assertThat(execution.getNote()).isEqualTo("완료 메모");
            assertThat(execution.getExpEarned()).isGreaterThan(0);
            verify(executionRepository).save(execution);
        }

        @Test
        @DisplayName("execution이 null이면 실패한다")
        void execute_failsWhenExecutionIsNull() {
            // given
            context.setExecution(null);

            // when
            SagaStepResult result = completeExecutionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Execution not loaded");
            verify(executionRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 완료된 수행 기록이면 실패한다")
        void execute_failsWhenAlreadyCompleted() {
            // given
            execution.setStatus(ExecutionStatus.COMPLETED);

            // when
            SagaStepResult result = completeExecutionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("이미 완료된 수행 기록");
            verify(executionRepository, never()).save(any());
        }

        @Test
        @DisplayName("미실행 처리된 수행 기록이면 실패한다")
        void execute_failsWhenMissed() {
            // given
            execution.setStatus(ExecutionStatus.MISSED);

            // when
            SagaStepResult result = completeExecutionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("미실행 처리된 수행 기록");
            verify(executionRepository, never()).save(any());
        }

        @Test
        @DisplayName("메모 없이 완료해도 성공한다")
        void execute_successWithoutNote() {
            // given
            context.setNote(null);
            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completeExecutionStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(execution.getNote()).isNull();
        }
    }

    @Nested
    @DisplayName("compensate 테스트")
    class CompensateTest {

        @Test
        @DisplayName("정상적으로 이전 상태로 복원한다")
        void compensate_success() {
            // given
            execution.setStatus(ExecutionStatus.COMPLETED);
            execution.setCompletedAt(LocalDateTime.now());
            execution.setExpEarned(30);
            execution.setNote("완료 메모");

            when(executionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completeExecutionStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
            assertThat(execution.getCompletedAt()).isNull();
            assertThat(execution.getExpEarned()).isEqualTo(0);
            assertThat(execution.getNote()).isNull();
            verify(executionRepository).save(execution);
        }

        @Test
        @DisplayName("execution이 null이면 아무 작업도 하지 않고 성공한다")
        void compensate_successWhenExecutionIsNull() {
            // given
            context.setExecution(null);

            // when
            SagaStepResult result = completeExecutionStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(executionRepository, never()).save(any());
        }
    }
}
