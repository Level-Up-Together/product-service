package io.pinkspider.leveluptogethermvp.missionservice.saga.steps;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.global.saga.SagaStepResult;
import io.pinkspider.leveluptogethermvp.missionservice.config.MissionExecutionProperties;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
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
@DisplayName("CompletePinnedInstanceStep 단위 테스트")
class CompletePinnedInstanceStepTest {

    @Mock
    private DailyMissionInstanceRepository instanceRepository;

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private MissionExecutionProperties missionExecutionProperties;

    @InjectMocks
    private CompletePinnedInstanceStep completePinnedInstanceStep;

    private static final String TEST_USER_ID = "test-user-pinned";
    private static final Long INSTANCE_ID = 100L;

    private Mission mission;
    private MissionParticipant participant;
    private DailyMissionInstance instance;
    private MissionCompletionContext context;

    @BeforeEach
    void setUp() {
        mission = Mission.builder()
            .title("매일 운동")
            .description("매일 운동하기 (고정 미션)")
            .creatorId(TEST_USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PRIVATE)
            .type(MissionType.PERSONAL)
            .isPinned(true)
            .expPerCompletion(10)
            .build();
        setId(mission, 1L);

        participant = MissionParticipant.builder()
            .mission(mission)
            .userId(TEST_USER_ID)
            .status(ParticipantStatus.IN_PROGRESS)
            .progress(0)
            .build();
        setId(participant, 1L);

        instance = DailyMissionInstance.createFrom(participant, LocalDate.now(), 1);
        instance.setStatus(ExecutionStatus.IN_PROGRESS);
        instance.setStartedAt(LocalDateTime.now().minusMinutes(15));
        setId(instance, INSTANCE_ID);

        context = MissionCompletionContext.forPinned(INSTANCE_ID, TEST_USER_ID, "고정 미션 완료", false);
        context.setInstance(instance);
        context.setParticipant(participant);
        context.setMission(mission);
        context.addCompensationData(
            MissionCompletionContext.CompensationKeys.INSTANCE_STATUS_BEFORE,
            ExecutionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Step 이름이 'CompletePinnedInstance'이다")
    void getName_returnsCorrectName() {
        assertThat(completePinnedInstanceStep.getName()).isEqualTo("CompletePinnedInstance");
    }

    @Test
    @DisplayName("shouldExecute는 pinned 컨텍스트에서만 true")
    void shouldExecute_onlyForPinned() {
        assertThat(completePinnedInstanceStep.shouldExecute().test(context)).isTrue();

        MissionCompletionContext nonPinned = new MissionCompletionContext(1L, TEST_USER_ID, null);
        assertThat(completePinnedInstanceStep.shouldExecute().test(nonPinned)).isFalse();
    }

    @Nested
    @DisplayName("execute 테스트")
    class ExecuteTest {

        @Test
        @DisplayName("정상적으로 인스턴스를 완료 처리한다")
        void execute_success() {
            // given
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(instance.getCompletedAt()).isNotNull();
            assertThat(instance.getNote()).isEqualTo("고정 미션 완료");
            verify(instanceRepository).save(instance);
        }

        @Test
        @DisplayName("instance가 null이면 실패한다")
        void execute_failsWhenInstanceIsNull() {
            // given
            context.setInstance(null);

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Instance not loaded");
            verify(instanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("SIMPLE 모드 + 일일 한도 미달이면 EXP=5, dailySimpleExpCapped=false")
        void execute_simpleMode_underDailyLimit_awardsExp() {
            // given
            mission.setExecutionMode(MissionExecutionMode.SIMPLE);
            when(executionRepository.countSimpleCompletedByUserIdAndDate(eq(TEST_USER_ID), any()))
                .thenReturn(2L);
            when(instanceRepository.countSimpleCompletedByUserIdAndDate(eq(TEST_USER_ID), any()))
                .thenReturn(3L);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(instance.getExpEarned()).isEqualTo(MissionExecutionMode.SIMPLE_EXP);
            assertThat(context.isDailySimpleExpCapped()).isFalse();
        }

        @Test
        @DisplayName("SIMPLE 모드 + 일일 한도 도달이면 EXP=0, dailySimpleExpCapped=true")
        void execute_simpleMode_atDailyLimit_capsExpToZero() {
            // given
            mission.setExecutionMode(MissionExecutionMode.SIMPLE);
            when(executionRepository.countSimpleCompletedByUserIdAndDate(eq(TEST_USER_ID), any()))
                .thenReturn(6L);
            when(instanceRepository.countSimpleCompletedByUserIdAndDate(eq(TEST_USER_ID), any()))
                .thenReturn(4L);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(instance.getExpEarned()).isEqualTo(0);
            assertThat(context.isDailySimpleExpCapped()).isTrue();
        }

        @Test
        @DisplayName("TIMED 모드는 SIMPLE 카운트 쿼리를 호출하지 않는다")
        void execute_timedMode_skipsSimpleCountQuery() {
            // given (default: TIMED)
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(executionRepository, never()).countSimpleCompletedByUserIdAndDate(any(), any());
            verify(instanceRepository, never()).countSimpleCompletedByUserIdAndDate(any(), any());
            assertThat(context.isDailySimpleExpCapped()).isFalse();
        }
    }

    @Nested
    @DisplayName("compensate 테스트")
    class CompensateTest {

        @Test
        @DisplayName("정상적으로 이전 상태로 복원한다")
        void compensate_success() {
            // given
            instance.setStatus(ExecutionStatus.COMPLETED);
            instance.setCompletedAt(LocalDateTime.now());
            instance.setExpEarned(5);
            instance.setNote("완료 메모");
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
            assertThat(instance.getCompletedAt()).isNull();
            assertThat(instance.getExpEarned()).isEqualTo(0);
            assertThat(instance.getNote()).isNull();
            verify(instanceRepository).save(instance);
        }

        @Test
        @DisplayName("instance가 null이면 아무 작업도 하지 않고 성공한다")
        void compensate_successWhenInstanceIsNull() {
            // given
            context.setInstance(null);

            // when
            SagaStepResult result = completePinnedInstanceStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(instanceRepository, never()).save(any());
        }
    }
}
