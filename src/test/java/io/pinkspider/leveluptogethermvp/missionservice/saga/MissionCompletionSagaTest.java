package io.pinkspider.leveluptogethermvp.missionservice.saga;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.saga.SagaEventPublisher;
import io.pinkspider.global.saga.SagaResult;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CompleteExecutionStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.CreateFeedFromMissionStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.GrantGuildExperienceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.GrantUserExperienceStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.LoadMissionDataStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.UpdateParticipantProgressStep;
import io.pinkspider.leveluptogethermvp.missionservice.saga.steps.UpdateUserStatsStep;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MissionCompletionSaga 테스트")
class MissionCompletionSagaTest {

    @Mock
    private LoadMissionDataStep loadMissionDataStep;
    @Mock
    private CompleteExecutionStep completeExecutionStep;
    @Mock
    private GrantUserExperienceStep grantUserExperienceStep;
    @Mock
    private GrantGuildExperienceStep grantGuildExperienceStep;
    @Mock
    private UpdateParticipantProgressStep updateParticipantProgressStep;
    @Mock
    private UpdateUserStatsStep updateUserStatsStep;
    @Mock
    private CreateFeedFromMissionStep createFeedFromMissionStep;
    @Mock
    private SagaEventPublisher sagaEventPublisher;

    @InjectMocks
    private MissionCompletionSaga missionCompletionSaga;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long EXECUTION_ID = 1L;

    private Mission mission;
    private MissionParticipant participant;
    private MissionExecution execution;

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
        execution.setStartedAt(LocalDateTime.now().minusMinutes(30));

        // Step 이름 및 기본 동작 설정
        when(loadMissionDataStep.getName()).thenReturn("LoadMissionData");
        when(loadMissionDataStep.shouldExecute()).thenReturn(ctx -> true);
        when(loadMissionDataStep.isMandatory()).thenReturn(true);
        when(loadMissionDataStep.getMaxRetries()).thenReturn(0);
        when(loadMissionDataStep.getRetryDelayMs()).thenReturn(1000L);

        when(completeExecutionStep.getName()).thenReturn("CompleteExecution");
        when(completeExecutionStep.shouldExecute()).thenReturn(ctx -> true);
        when(completeExecutionStep.isMandatory()).thenReturn(true);
        when(completeExecutionStep.getMaxRetries()).thenReturn(0);
        when(completeExecutionStep.getRetryDelayMs()).thenReturn(1000L);

        when(grantUserExperienceStep.getName()).thenReturn("GrantUserExperience");
        when(grantUserExperienceStep.shouldExecute()).thenReturn(ctx -> true);
        when(grantUserExperienceStep.isMandatory()).thenReturn(true);
        when(grantUserExperienceStep.getMaxRetries()).thenReturn(0);
        when(grantUserExperienceStep.getRetryDelayMs()).thenReturn(1000L);

        when(grantGuildExperienceStep.getName()).thenReturn("GrantGuildExperience");
        when(grantGuildExperienceStep.shouldExecute()).thenReturn(ctx -> true);
        when(grantGuildExperienceStep.isMandatory()).thenReturn(true);
        when(grantGuildExperienceStep.getMaxRetries()).thenReturn(0);
        when(grantGuildExperienceStep.getRetryDelayMs()).thenReturn(1000L);

        when(updateParticipantProgressStep.getName()).thenReturn("UpdateParticipantProgress");
        when(updateParticipantProgressStep.shouldExecute()).thenReturn(ctx -> true);
        when(updateParticipantProgressStep.isMandatory()).thenReturn(true);
        when(updateParticipantProgressStep.getMaxRetries()).thenReturn(0);
        when(updateParticipantProgressStep.getRetryDelayMs()).thenReturn(1000L);

        when(updateUserStatsStep.getName()).thenReturn("UpdateUserStats");
        when(updateUserStatsStep.shouldExecute()).thenReturn(ctx -> true);
        when(updateUserStatsStep.isMandatory()).thenReturn(false);
        when(updateUserStatsStep.getMaxRetries()).thenReturn(0);
        when(updateUserStatsStep.getRetryDelayMs()).thenReturn(1000L);

        when(createFeedFromMissionStep.getName()).thenReturn("CreateFeedFromMission");
        when(createFeedFromMissionStep.shouldExecute()).thenReturn(ctx -> true);
        when(createFeedFromMissionStep.isMandatory()).thenReturn(false);
        when(createFeedFromMissionStep.getMaxRetries()).thenReturn(0);
        when(createFeedFromMissionStep.getRetryDelayMs()).thenReturn(1000L);
    }

    @Nested
    @DisplayName("정상 흐름 테스트")
    class SuccessFlowTest {

        @Test
        @DisplayName("모든 Step이 성공하면 Saga가 성공한다")
        void execute_successWhenAllStepsSucceed() {
            // given
            when(loadMissionDataStep.execute(any(MissionCompletionContext.class)))
                .thenAnswer(invocation -> {
                    MissionCompletionContext ctx = invocation.getArgument(0);
                    ctx.setExecution(execution);
                    ctx.setParticipant(participant);
                    ctx.setMission(mission);
                    ctx.setUserExpEarned(50);
                    return SagaStepResult.success("데이터 로드 성공");
                });
            when(completeExecutionStep.execute(any())).thenReturn(SagaStepResult.success("완료 처리됨"));
            when(grantUserExperienceStep.execute(any())).thenReturn(SagaStepResult.success("경험치 지급됨"));
            when(grantGuildExperienceStep.execute(any())).thenReturn(SagaStepResult.success("길드 경험치 스킵"));
            when(updateParticipantProgressStep.execute(any())).thenReturn(SagaStepResult.success("진행도 업데이트됨"));
            when(updateUserStatsStep.execute(any())).thenReturn(SagaStepResult.success("통계 업데이트됨"));
            when(createFeedFromMissionStep.execute(any())).thenReturn(SagaStepResult.success("피드 스킵"));

            // when
            SagaResult<MissionCompletionContext> result = missionCompletionSaga.execute(EXECUTION_ID, TEST_USER_ID, "완료 메모");

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getContext()).isNotNull();
            assertThat(result.getContext().getExecution()).isEqualTo(execution);

            // 모든 Step이 실행됨을 확인
            verify(loadMissionDataStep).execute(any());
            verify(completeExecutionStep).execute(any());
            verify(grantUserExperienceStep).execute(any());
            verify(grantGuildExperienceStep).execute(any());
            verify(updateParticipantProgressStep).execute(any());
            verify(updateUserStatsStep).execute(any());
            verify(createFeedFromMissionStep).execute(any());
        }
    }

    @Nested
    @DisplayName("실패 및 보상 흐름 테스트")
    class FailureAndCompensationFlowTest {

        @Test
        @DisplayName("필수 Step이 실패하면 이전 Step들이 보상된다")
        void execute_compensatesWhenMandatoryStepFails() {
            // given
            when(loadMissionDataStep.execute(any(MissionCompletionContext.class)))
                .thenAnswer(invocation -> {
                    MissionCompletionContext ctx = invocation.getArgument(0);
                    ctx.setExecution(execution);
                    ctx.setParticipant(participant);
                    ctx.setMission(mission);
                    return SagaStepResult.success("데이터 로드 성공");
                });
            when(completeExecutionStep.execute(any())).thenReturn(SagaStepResult.success("완료 처리됨"));
            when(grantUserExperienceStep.execute(any()))
                .thenReturn(SagaStepResult.failure("경험치 지급 실패"));
            when(grantUserExperienceStep.isMandatory()).thenReturn(true);

            // 보상 메서드
            when(completeExecutionStep.compensate(any())).thenReturn(SagaStepResult.success("완료 보상됨"));
            when(loadMissionDataStep.compensate(any())).thenReturn(SagaStepResult.success("로드 보상됨"));

            // when
            SagaResult<MissionCompletionContext> result = missionCompletionSaga.execute(EXECUTION_ID, TEST_USER_ID, null);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("경험치 지급 실패");

            // 성공한 Step들이 보상됨을 확인
            verify(completeExecutionStep).compensate(any());
            verify(loadMissionDataStep).compensate(any());

            // 실패 후 Step들은 실행되지 않음
            verify(grantGuildExperienceStep, never()).execute(any());
            verify(updateParticipantProgressStep, never()).execute(any());
        }

        @Test
        @DisplayName("첫 번째 Step 실패 시 보상 없이 바로 실패 반환")
        void execute_noCompensationWhenFirstStepFails() {
            // given
            when(loadMissionDataStep.execute(any()))
                .thenReturn(SagaStepResult.failure("수행 기록을 찾을 수 없습니다"));

            // when
            SagaResult<MissionCompletionContext> result = missionCompletionSaga.execute(999L, TEST_USER_ID, null);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("수행 기록을 찾을 수 없습니다");

            // 다른 Step들은 실행되지 않음
            verify(completeExecutionStep, never()).execute(any());
            verify(grantUserExperienceStep, never()).execute(any());
        }

        @Test
        @DisplayName("선택적 Step 실패는 전체 Saga를 실패시키지 않는다")
        void execute_continuesWhenOptionalStepFails() {
            // given
            when(loadMissionDataStep.execute(any(MissionCompletionContext.class)))
                .thenAnswer(invocation -> {
                    MissionCompletionContext ctx = invocation.getArgument(0);
                    ctx.setExecution(execution);
                    ctx.setParticipant(participant);
                    ctx.setMission(mission);
                    return SagaStepResult.success("데이터 로드 성공");
                });
            when(completeExecutionStep.execute(any())).thenReturn(SagaStepResult.success("완료 처리됨"));
            when(grantUserExperienceStep.execute(any())).thenReturn(SagaStepResult.success("경험치 지급됨"));
            when(grantGuildExperienceStep.execute(any())).thenReturn(SagaStepResult.success("길드 경험치 스킵"));
            when(updateParticipantProgressStep.execute(any())).thenReturn(SagaStepResult.success("진행도 업데이트됨"));

            // 선택적 Step 실패
            when(updateUserStatsStep.execute(any())).thenReturn(SagaStepResult.failure("통계 업데이트 실패"));
            when(updateUserStatsStep.isMandatory()).thenReturn(false);

            when(createFeedFromMissionStep.execute(any())).thenReturn(SagaStepResult.success("피드 스킵"));

            // when
            SagaResult<MissionCompletionContext> result = missionCompletionSaga.execute(EXECUTION_ID, TEST_USER_ID, null);

            // then
            assertThat(result.isSuccess()).isTrue();

            // 실패한 선택적 Step 이후에도 계속 실행됨
            verify(createFeedFromMissionStep).execute(any());
        }
    }

    @Nested
    @DisplayName("toResponse 테스트")
    class ToResponseTest {

        @Test
        @DisplayName("성공한 Saga 결과에서 MissionExecutionResponse를 추출한다")
        void toResponse_extractsResponseFromSuccessResult() {
            // given
            when(loadMissionDataStep.execute(any(MissionCompletionContext.class)))
                .thenAnswer(invocation -> {
                    MissionCompletionContext ctx = invocation.getArgument(0);
                    ctx.setExecution(execution);
                    ctx.setParticipant(participant);
                    ctx.setMission(mission);
                    return SagaStepResult.success("데이터 로드 성공");
                });
            when(completeExecutionStep.execute(any())).thenReturn(SagaStepResult.success("완료"));
            when(grantUserExperienceStep.execute(any())).thenReturn(SagaStepResult.success("경험치"));
            when(grantGuildExperienceStep.execute(any())).thenReturn(SagaStepResult.success("길드"));
            when(updateParticipantProgressStep.execute(any())).thenReturn(SagaStepResult.success("진행도"));
            when(updateUserStatsStep.execute(any())).thenReturn(SagaStepResult.success("통계"));
            when(createFeedFromMissionStep.execute(any())).thenReturn(SagaStepResult.success("피드"));

            SagaResult<MissionCompletionContext> result = missionCompletionSaga.execute(EXECUTION_ID, TEST_USER_ID, null);

            // when
            var response = missionCompletionSaga.toResponse(result);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(EXECUTION_ID);
        }
    }
}
