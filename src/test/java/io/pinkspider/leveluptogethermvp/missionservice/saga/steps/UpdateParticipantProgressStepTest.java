package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.saga.MissionCompletionContext;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateParticipantProgressStep 단위 테스트")
class UpdateParticipantProgressStepTest {

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @InjectMocks
    private UpdateParticipantProgressStep updateParticipantProgressStep;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long PARTICIPANT_ID = 1L;

    private Mission mission;
    private MissionParticipant participant;
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
            .durationDays(30)
            .build();
        setId(mission, 1L);

        participant = MissionParticipant.builder()
            .mission(mission)
            .userId(TEST_USER_ID)
            .status(ParticipantStatus.IN_PROGRESS)
            .progress(0)
            .build();
        setId(participant, PARTICIPANT_ID);

        context = new MissionCompletionContext(1L, TEST_USER_ID, null);
        context.setParticipant(participant);
        context.setMission(mission);
    }

    private void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Step 이름이 'UpdateParticipantProgress'이다")
    void getName_returnsCorrectName() {
        assertThat(updateParticipantProgressStep.getName()).isEqualTo("UpdateParticipantProgress");
    }

    @Nested
    @DisplayName("execute 테스트")
    class ExecuteTest {

        @Test
        @DisplayName("정상적으로 참가자 진행도를 업데이트한다")
        void execute_success() {
            // given
            MissionExecution execution1 = MissionExecution.builder()
                .participant(participant)
                .executionDate(LocalDate.now().minusDays(1))
                .status(ExecutionStatus.COMPLETED)
                .build();
            MissionExecution execution2 = MissionExecution.builder()
                .participant(participant)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.COMPLETED)
                .build();
            MissionExecution execution3 = MissionExecution.builder()
                .participant(participant)
                .executionDate(LocalDate.now().plusDays(1))
                .status(ExecutionStatus.PENDING)
                .build();

            when(executionRepository.findByParticipantId(PARTICIPANT_ID))
                .thenReturn(List.of(execution1, execution2, execution3));
            when(executionRepository.countByParticipantIdAndStatus(PARTICIPANT_ID, ExecutionStatus.COMPLETED))
                .thenReturn(2L);
            when(participantRepository.save(any(MissionParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = updateParticipantProgressStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            // 3개 중 2개 완료 = 66%
            assertThat(participant.getProgress()).isEqualTo(66);
            verify(participantRepository).save(participant);
        }

        @Test
        @DisplayName("참가자가 null이면 실패한다")
        void execute_failsWhenParticipantIsNull() {
            // given
            context.setParticipant(null);

            // when
            SagaStepResult result = updateParticipantProgressStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Participant not loaded");
            verify(participantRepository, never()).save(any());
        }

        @Test
        @DisplayName("실행 기록이 없으면 진행도가 0%이다")
        void execute_zeroProgressWhenNoExecutions() {
            // given
            when(executionRepository.findByParticipantId(PARTICIPANT_ID))
                .thenReturn(List.of());
            when(participantRepository.save(any(MissionParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = updateParticipantProgressStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(participant.getProgress()).isEqualTo(0);
        }

        @Test
        @DisplayName("모든 수행이 완료되면 진행도가 100%이다")
        void execute_fullProgressWhenAllCompleted() {
            // given
            MissionExecution execution1 = MissionExecution.builder()
                .participant(participant)
                .executionDate(LocalDate.now().minusDays(1))
                .status(ExecutionStatus.COMPLETED)
                .build();
            MissionExecution execution2 = MissionExecution.builder()
                .participant(participant)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.COMPLETED)
                .build();

            when(executionRepository.findByParticipantId(PARTICIPANT_ID))
                .thenReturn(List.of(execution1, execution2));
            when(executionRepository.countByParticipantIdAndStatus(PARTICIPANT_ID, ExecutionStatus.COMPLETED))
                .thenReturn(2L);
            when(participantRepository.save(any(MissionParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = updateParticipantProgressStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(participant.getProgress()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("compensate 테스트")
    class CompensateTest {

        @Test
        @DisplayName("정상적으로 이전 상태로 복원한다")
        void compensate_success() {
            // given
            participant.updateProgress(50);
            context.addCompensationData(
                MissionCompletionContext.CompensationKeys.PARTICIPANT_PROGRESS_BEFORE, 10);
            context.addCompensationData(
                MissionCompletionContext.CompensationKeys.PARTICIPANT_STATUS_BEFORE, ParticipantStatus.IN_PROGRESS);

            when(participantRepository.save(any(MissionParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = updateParticipantProgressStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(participant.getProgress()).isEqualTo(10);
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.IN_PROGRESS);
            verify(participantRepository).save(participant);
        }

        @Test
        @DisplayName("참가자가 null이면 아무 작업도 하지 않고 성공한다")
        void compensate_successWhenParticipantIsNull() {
            // given
            context.setParticipant(null);

            // when
            SagaStepResult result = updateParticipantProgressStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(participantRepository, never()).save(any());
        }
    }
}
