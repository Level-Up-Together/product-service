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
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
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

    @Mock private DailyMissionInstanceRepository instanceRepository;

    @Mock private MissionExecutionRepository executionRepository;

    @InjectMocks private CompletePinnedInstanceStep completePinnedInstanceStep;

    private static final String TEST_USER_ID = "test-user-pinned";
    private static final Long INSTANCE_ID = 100L;

    private Mission mission;
    private MissionParticipant participant;
    private DailyMissionInstance instance;
    private MissionCompletionContext context;

    @BeforeEach
    void setUp() {
        mission =
                Mission.builder()
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

        participant =
                MissionParticipant.builder()
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
        @DisplayName("LUT-568: 고정 체크 미션도 당일 누계 3개째면 마일스톤 3 / 보너스 20 을 기록한다 (일반 0 + 고정 2 + 이번)")
        void execute_simpleMode_thirdCompletion_marksMilestone3() {
            // given
            mission.setExecutionMode(MissionExecutionMode.SIMPLE);
            when(executionRepository.countSimpleCompletedByUserIdAndDate(eq(TEST_USER_ID), any()))
                    .thenReturn(0L);
            when(instanceRepository.countSimpleCompletedByUserIdAndDate(eq(TEST_USER_ID), any()))
                    .thenReturn(2L);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(instance.getExpEarned()).isEqualTo(10);
            assertThat(context.getMilestoneCount()).isEqualTo(3);
            assertThat(context.getMilestoneBonusExp()).isEqualTo(20);
        }

        @Test
        @DisplayName("LUT-568: 한도 초과(11개째)로 EXP 0 이어도 마일스톤 판정은 누계로 하되 11 은 대상이 아니다")
        void execute_simpleMode_overLimit_noMilestone() {
            // given
            mission.setExecutionMode(MissionExecutionMode.SIMPLE);
            when(executionRepository.countSimpleCompletedByUserIdAndDate(eq(TEST_USER_ID), any()))
                    .thenReturn(10L);
            when(instanceRepository.countSimpleCompletedByUserIdAndDate(eq(TEST_USER_ID), any()))
                    .thenReturn(0L);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            completePinnedInstanceStep.execute(context);

            // then
            assertThat(instance.getExpEarned()).isZero();
            assertThat(context.isDailySimpleExpCapped()).isTrue();
            assertThat(context.getMilestoneBonusExp()).isZero();
        }

        @Test
        @DisplayName(
                "LUT-568: 한도 초과로 EXP 0 인 완료라도 누계 5개째면 마일스톤 5 를 기록한다 — 가능한 조합(일반 4 + 고정 0 + 이번)")
        void execute_simpleMode_fifthCompletion_marksMilestone5() {
            // given
            mission.setExecutionMode(MissionExecutionMode.SIMPLE);
            when(executionRepository.countSimpleCompletedByUserIdAndDate(eq(TEST_USER_ID), any()))
                    .thenReturn(4L);
            when(instanceRepository.countSimpleCompletedByUserIdAndDate(eq(TEST_USER_ID), any()))
                    .thenReturn(0L);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            completePinnedInstanceStep.execute(context);

            // then
            assertThat(context.getMilestoneCount()).isEqualTo(5);
            assertThat(context.getMilestoneBonusExp()).isEqualTo(20);
        }

        @Test
        @DisplayName("LUT-568: 유저 고정 타이머 120분 목표 도달은 공식값 72 로 되돌린다")
        void execute_userPinnedTimer_target120_appliesFormula() {
            // given
            instance.setTargetDurationMinutes(120);
            instance.setBonusExpOnFullCompletion(7);
            instance.setStartedAt(LocalDateTime.now().minusMinutes(130));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then: timedExp(120) = 60 + 12
            assertThat(result.isSuccess()).isTrue();
            assertThat(instance.getExpEarned()).isEqualTo(72);
            assertThat(context.getUserExpEarned()).isEqualTo(72);
        }

        @Test
        @DisplayName("LUT-568: 미션북 고정 타이머 120분 목표 도달은 공식값 72 + 보너스")
        void execute_systemPinnedTimer_target120_formulaPlusBonus() {
            // given
            mission.setSource(MissionSource.SYSTEM);
            instance.setTargetDurationMinutes(120);
            instance.setBonusExpOnFullCompletion(30);
            instance.setStartedAt(LocalDateTime.now().minusMinutes(130));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            completePinnedInstanceStep.execute(context);

            // then
            assertThat(instance.getExpEarned()).isEqualTo(102);
            assertThat(context.getFullCompletionBonusExp()).isEqualTo(30);
        }

        @Test
        @DisplayName("SIMPLE 모드 + 일일 한도 미달이면 EXP=10, dailySimpleExpCapped=false")
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
        @DisplayName("QA-198: 길드 고정 미션은 user EXP 와 동일한 값을 guild EXP 로 context 에 set 한다")
        void execute_guildPinnedMission_setsGuildExpEarned() {
            // given
            mission.setType(MissionType.GUILD);
            mission.setGuildId("777");
            // CompletePinnedInstanceStep 에서 instance.setExpEarned(...) 로 채워지지만 mock save 라
            // 직접 사전 세팅한다 (TIMED 기본 모드, complete() 동작은 다른 테스트에서 검증)
            instance.setExpEarned(42);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getGuildExpEarned()).isEqualTo(instance.getExpEarned());
            assertThat(context.getUserExpEarned()).isEqualTo(instance.getExpEarned());
        }

        @Test
        @DisplayName("QA-198: 개인 고정 미션은 guild EXP 를 set 하지 않는다 (기본 0)")
        void execute_personalPinnedMission_doesNotSetGuildExpEarned() {
            // given (default: PERSONAL)
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getGuildExpEarned()).isEqualTo(0);
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

        @Test
        @DisplayName("mission이 null이면 SIMPLE 판정·목표시간 판정을 건너뛰고 완료 처리한다")
        void execute_missionNull_skipsModeChecks() {
            // given
            context.setMission(null);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(context.isFullCompletionBonusGranted()).isFalse();
            verify(executionRepository, never()).countSimpleCompletedByUserIdAndDate(any(), any());
        }

        @Test
        @DisplayName("SIMPLE 모드라도 instanceDate가 null이면 일일 한도 카운트를 조회하지 않는다")
        void execute_simpleMode_instanceDateNull_skipsCountQuery() {
            // given
            mission.setExecutionMode(MissionExecutionMode.SIMPLE);
            instance.setInstanceDate(null);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(instance.getExpEarned()).isEqualTo(MissionExecutionMode.SIMPLE_EXP);
            verify(executionRepository, never()).countSimpleCompletedByUserIdAndDate(any(), any());
            verify(instanceRepository, never()).countSimpleCompletedByUserIdAndDate(any(), any());
        }

        @Test
        @DisplayName("note가 null이면 인스턴스 note를 덮어쓰지 않는다")
        void execute_noteNull_doesNotOverrideNote() {
            // given
            MissionCompletionContext noNoteContext =
                    MissionCompletionContext.forPinned(INSTANCE_ID, TEST_USER_ID, null, false);
            noNoteContext.setInstance(instance);
            noNoteContext.setParticipant(participant);
            noNoteContext.setMission(mission);
            instance.setNote("기존 메모");
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(noNoteContext);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(instance.getNote()).isEqualTo("기존 메모");
        }

        @Test
        @DisplayName("목표시간 달성 시 fullCompletionBonus 를 context 에 반영한다 (bonus 설정)")
        void execute_targetDurationReached_setsFullCompletionBonus() {
            // given — 보너스는 미션북(SYSTEM) 전용 (LUT-551)
            mission.setSource(MissionSource.SYSTEM);
            instance.setTargetDurationMinutes(10);
            instance.setBonusExpOnFullCompletion(7);
            instance.setStartedAt(LocalDateTime.now().minusMinutes(15));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.isFullCompletionBonusGranted()).isTrue();
            assertThat(context.getFullCompletionBonusExp()).isEqualTo(7);
            assertThat(context.getUserExpEarned()).isEqualTo(17);
        }

        @Test
        @DisplayName("목표시간 달성 + bonus 미설정(null)이면 보너스 EXP 0 으로 기록한다")
        void execute_targetDurationReached_bonusNull_setsZeroBonus() {
            // given
            mission.setSource(MissionSource.SYSTEM);
            instance.setTargetDurationMinutes(10);
            instance.setBonusExpOnFullCompletion(null);
            instance.setStartedAt(LocalDateTime.now().minusMinutes(15));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.isFullCompletionBonusGranted()).isTrue();
            assertThat(context.getFullCompletionBonusExp()).isEqualTo(0);
        }

        @Test
        @DisplayName("LUT-551: 유저 생성(USER) 고정 타이머 미션은 목표 도달 시 보너스 없이 목표분으로 EXP 를 되돌린다")
        void execute_userPinnedTimerMission_targetReached_noBonus() {
            // given — 미션에서 인스턴스로 복사된 bonus(7) 가 complete() 에서 가산되지만 USER 미션이라 되돌려야 한다
            instance.setTargetDurationMinutes(10);
            instance.setBonusExpOnFullCompletion(7);
            instance.setStartedAt(LocalDateTime.now().minusMinutes(15));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(instance.getExpEarned()).isEqualTo(10);
            assertThat(context.getUserExpEarned()).isEqualTo(10);
            assertThat(context.isFullCompletionBonusGranted()).isFalse();
        }

        @Test
        @DisplayName("목표시간 미달이면 fullCompletionBonus 를 부여하지 않는다")
        void execute_targetDurationNotReached_noBonus() {
            // given
            instance.setTargetDurationMinutes(60);
            instance.setBonusExpOnFullCompletion(7);
            instance.setStartedAt(LocalDateTime.now().minusMinutes(15));
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.isFullCompletionBonusGranted()).isFalse();
            assertThat(context.getFullCompletionBonusExp()).isEqualTo(0);
        }

        @Test
        @DisplayName("목표시간이 0이면 목표시간 판정을 건너뛴다")
        void execute_targetDurationZero_skipsBonusCheck() {
            // given
            instance.setTargetDurationMinutes(0);
            instance.setBonusExpOnFullCompletion(7);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.isFullCompletionBonusGranted()).isFalse();
        }

        @Test
        @DisplayName("SIMPLE 모드는 목표시간이 설정돼도 fullCompletionBonus 판정을 하지 않는다")
        void execute_simpleMode_skipsBonusCheckEvenWithTarget() {
            // given
            mission.setExecutionMode(MissionExecutionMode.SIMPLE);
            instance.setTargetDurationMinutes(10);
            instance.setBonusExpOnFullCompletion(7);
            when(executionRepository.countSimpleCompletedByUserIdAndDate(eq(TEST_USER_ID), any()))
                    .thenReturn(0L);
            when(instanceRepository.countSimpleCompletedByUserIdAndDate(eq(TEST_USER_ID), any()))
                    .thenReturn(0L);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(context.isFullCompletionBonusGranted()).isFalse();
            assertThat(instance.getExpEarned()).isEqualTo(MissionExecutionMode.SIMPLE_EXP);
        }

        @Test
        @DisplayName("이미 완료된 인스턴스는 예외를 잡아 실패 결과를 반환한다")
        void execute_alreadyCompleted_returnsFailure() {
            // given
            instance.setStatus(ExecutionStatus.COMPLETED);

            // when
            SagaStepResult result = completePinnedInstanceStep.execute(context);

            // then
            assertThat(result.isSuccess()).isFalse();
            verify(instanceRepository, never()).save(any());
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

        @Test
        @DisplayName("보상 데이터(이전 상태)가 없으면 복원하지 않고 성공한다")
        void compensate_noPreviousStatus_skipsRestore() {
            // given
            MissionCompletionContext noDataContext =
                    MissionCompletionContext.forPinned(INSTANCE_ID, TEST_USER_ID, "메모", false);
            instance.setStatus(ExecutionStatus.COMPLETED);
            instance.setExpEarned(5);
            noDataContext.setInstance(instance);

            // when
            SagaStepResult result = completePinnedInstanceStep.compensate(noDataContext);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(instance.getExpEarned()).isEqualTo(5);
            verify(instanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("복원 중 저장 예외가 발생하면 실패 결과를 반환한다")
        void compensate_saveThrows_returnsFailure() {
            // given
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenThrow(new RuntimeException("db down"));

            // when
            SagaStepResult result = completePinnedInstanceStep.compensate(context);

            // then
            assertThat(result.isSuccess()).isFalse();
        }
    }
}
