package io.pinkspider.leveluptogethermvp.missionservice.scheduler;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.global.test.TestReflectionUtils;
import io.pinkspider.leveluptogethermvp.missionservice.application.DailyMissionInstanceService;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionService;
import io.pinkspider.leveluptogethermvp.missionservice.config.MissionExecutionProperties;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DailyMissionInstanceScheduler 테스트")
class DailyMissionInstanceSchedulerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private LocalDate today() {
        return LocalDate.now(KST);
    }

    @Mock private DailyMissionInstanceRepository instanceRepository;

    @Mock private MissionExecutionRepository executionRepository;

    @Mock private MissionParticipantRepository participantRepository;

    @Mock private DailyMissionInstanceService dailyMissionInstanceService;

    @Mock private MissionExecutionService missionExecutionService;

    @Mock private MissionExecutionProperties missionExecutionProperties;

    @Mock private io.pinkspider.global.facade.GamificationQueryFacade gamificationQueryFacade;

    @InjectMocks private DailyMissionInstanceScheduler scheduler;

    @Captor private ArgumentCaptor<List<DailyMissionInstance>> instanceListCaptor;

    private static final String USER_ID_1 = "user-1";
    private static final String USER_ID_2 = "user-2";

    private Mission mission;
    private MissionParticipant participant1;
    private MissionParticipant participant2;

    @BeforeEach
    void setUp() {
        when(missionExecutionProperties.getBaseExp()).thenReturn(10);

        mission =
                Mission.builder()
                        .title("매일 30분 운동")
                        .description("매일 30분씩 운동하기")
                        .creatorId(USER_ID_1)
                        .status(MissionStatus.IN_PROGRESS)
                        .visibility(MissionVisibility.PRIVATE)
                        .type(MissionType.PERSONAL)
                        .categoryId(1L)
                        .categoryName("운동")
                        .expPerCompletion(50)
                        .isPinned(true)
                        .build();
        setId(mission, 1L);

        participant1 =
                MissionParticipant.builder()
                        .mission(mission)
                        .userId(USER_ID_1)
                        .status(ParticipantStatus.ACCEPTED)
                        .build();
        setId(participant1, 1L);

        participant2 =
                MissionParticipant.builder()
                        .mission(mission)
                        .userId(USER_ID_2)
                        .status(ParticipantStatus.ACCEPTED)
                        .build();
        setId(participant2, 2L);
    }

    @Nested
    @DisplayName("generateDailyInstances 테스트")
    class GenerateDailyInstancesTest {

        @Test
        @DisplayName("활성 참여자에 대해 오늘 인스턴스를 생성한다")
        void generateDailyInstances_success() {
            // given
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
            when(missionExecutionService.markMissedExecutions()).thenReturn(0);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of(participant1, participant2));
            when(instanceRepository.existsByParticipantIdAndInstanceDate(
                            eq(1L), any(LocalDate.class)))
                    .thenReturn(false);
            when(instanceRepository.existsByParticipantIdAndInstanceDate(
                            eq(2L), any(LocalDate.class)))
                    .thenReturn(false);
            when(instanceRepository.saveAll(any()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            scheduler.generateDailyInstances();

            // then
            verify(instanceRepository).markMissedInstances(any(LocalDate.class));
            verify(instanceRepository).saveAll(instanceListCaptor.capture());

            List<DailyMissionInstance> savedInstances = instanceListCaptor.getValue();
            assertThat(savedInstances).hasSize(2);
        }

        @Test
        @DisplayName("활성 참여자가 없으면 인스턴스를 생성하지 않는다")
        void generateDailyInstances_noParticipants() {
            // given
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
            when(missionExecutionService.markMissedExecutions()).thenReturn(0);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of());

            // when
            scheduler.generateDailyInstances();

            // then
            verify(instanceRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("이미 오늘 인스턴스가 있으면 생성을 건너뛴다")
        void generateDailyInstances_skipsExisting() {
            // given
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
            when(missionExecutionService.markMissedExecutions()).thenReturn(0);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of(participant1, participant2));
            when(instanceRepository.existsByParticipantIdAndInstanceDate(
                            eq(1L), any(LocalDate.class)))
                    .thenReturn(true); // participant1은 이미 존재
            when(instanceRepository.existsByParticipantIdAndInstanceDate(
                            eq(2L), any(LocalDate.class)))
                    .thenReturn(false);

            // when
            scheduler.generateDailyInstances();

            // then
            verify(instanceRepository).saveAll(instanceListCaptor.capture());

            List<DailyMissionInstance> savedInstances = instanceListCaptor.getValue();
            assertThat(savedInstances).hasSize(1); // participant2만 생성
            assertThat(savedInstances.get(0).getParticipant().getUserId()).isEqualTo(USER_ID_2);
        }

        @Test
        @DisplayName("미시작(PENDING) 인스턴스를 MISSED 처리한다")
        void generateDailyInstances_marksMissed() {
            // given
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(5);
            when(missionExecutionService.markMissedExecutions()).thenReturn(0);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of());

            // when
            scheduler.generateDailyInstances();

            // then
            verify(instanceRepository).markMissedInstances(any(LocalDate.class));
        }
    }

    @Nested
    @DisplayName("자정 자동 완료 테스트")
    class MidnightAutoCompleteTest {

        @Test
        @DisplayName("지난 날짜 IN_PROGRESS 고정 미션을 Saga로 자동 완료한다")
        void autoCompletePastDayInstances_viaSaga() {
            // given
            DailyMissionInstance inProgressInstance =
                    DailyMissionInstance.createFrom(participant1, today().minusDays(1));
            setId(inProgressInstance, 100L);
            inProgressInstance.start();
            TestReflectionUtils.setField(
                    inProgressInstance, "startedAt", LocalDateTime.now().minusHours(5));

            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(inProgressInstance));
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
            when(missionExecutionService.markMissedExecutions()).thenReturn(0);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of());

            // when
            scheduler.generateDailyInstances();

            // then
            verify(dailyMissionInstanceService)
                    .completeInstance(eq(100L), eq(USER_ID_1), isNull(), eq(false));
        }

        @Test
        @DisplayName("지난 날짜 IN_PROGRESS 일반 미션을 Saga로 자동 완료한다")
        void autoCompletePastDayExecutions_viaSaga() {
            // given
            MissionExecution inProgressExecution =
                    MissionExecution.builder()
                            .participant(participant1)
                            .executionDate(today().minusDays(1))
                            .status(ExecutionStatus.IN_PROGRESS)
                            .startedAt(LocalDateTime.now().minusHours(5))
                            .build();
            setId(inProgressExecution, 200L);

            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(inProgressExecution));
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
            when(missionExecutionService.markMissedExecutions()).thenReturn(0);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of());

            // when
            scheduler.generateDailyInstances();

            // then
            verify(missionExecutionService)
                    .completeExecution(eq(200L), eq(USER_ID_1), isNull(), eq(false));
        }

        @Test
        @DisplayName("Saga 실패 시 엔티티 레벨 직접 완료로 폴백한다")
        void autoCompletePastDayInstances_fallbackOnSagaFailure() {
            // given
            DailyMissionInstance inProgressInstance =
                    DailyMissionInstance.createFrom(participant1, today().minusDays(1));
            setId(inProgressInstance, 100L);
            inProgressInstance.start();
            TestReflectionUtils.setField(
                    inProgressInstance, "startedAt", LocalDateTime.now().minusHours(5));

            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(inProgressInstance));
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
            when(missionExecutionService.markMissedExecutions()).thenReturn(0);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of());
            doThrow(new RuntimeException("Saga 실패"))
                    .when(dailyMissionInstanceService)
                    .completeInstance(any(), anyString(), isNull(), anyBoolean());

            // when
            scheduler.generateDailyInstances();

            // then - 폴백으로 엔티티 직접 완료 (5시간 경과 > 4시간 상한 → baseExp 지급)
            assertThat(inProgressInstance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(inProgressInstance.getExpEarned()).isEqualTo(10);
            assertThat(inProgressInstance.getIsAutoCompleted()).isTrue();
            verify(instanceRepository).save(inProgressInstance);
            // QA-141: 폴백 시에도 gamification 경험치 지급
            verify(gamificationQueryFacade)
                    .addExperience(
                            eq(USER_ID_1),
                            eq(10),
                            eq(io.pinkspider.global.enums.ExpSourceType.MISSION_EXECUTION),
                            eq(1L),
                            anyString(),
                            eq(1L),
                            eq("운동"));
        }

        @Test
        @DisplayName("QA-141: 일반 미션 Saga 실패 폴백 시에도 gamification 경험치를 지급한다")
        void autoCompletePastDayExecutions_fallbackGrantsExpToGamification() {
            // given
            Mission regularMission =
                    Mission.builder()
                            .title("매일 1시간 공부")
                            .creatorId(USER_ID_1)
                            .status(MissionStatus.IN_PROGRESS)
                            .visibility(MissionVisibility.PRIVATE)
                            .type(MissionType.PERSONAL)
                            .categoryId(2L)
                            .categoryName("학습")
                            .isPinned(false)
                            .build();
            setId(regularMission, 9L);
            MissionParticipant regularParticipant =
                    MissionParticipant.builder()
                            .mission(regularMission)
                            .userId(USER_ID_1)
                            .status(ParticipantStatus.ACCEPTED)
                            .build();
            setId(regularParticipant, 9L);

            MissionExecution inProgressExecution =
                    MissionExecution.builder()
                            .participant(regularParticipant)
                            .executionDate(today().minusDays(1))
                            .status(ExecutionStatus.IN_PROGRESS)
                            .startedAt(LocalDateTime.now().minusHours(5))
                            .build();
            setId(inProgressExecution, 300L);

            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(inProgressExecution));
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
            when(missionExecutionService.markMissedExecutions()).thenReturn(0);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of());
            doThrow(new RuntimeException("Saga 실패"))
                    .when(missionExecutionService)
                    .completeExecution(any(Long.class), anyString(), isNull(), anyBoolean());

            // when
            scheduler.generateDailyInstances();

            // then
            assertThat(inProgressExecution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(inProgressExecution.getExpEarned()).isEqualTo(10);
            assertThat(inProgressExecution.getIsAutoCompleted()).isTrue();
            verify(executionRepository).save(inProgressExecution);
            verify(gamificationQueryFacade)
                    .addExperience(
                            eq(USER_ID_1),
                            eq(10),
                            eq(io.pinkspider.global.enums.ExpSourceType.MISSION_EXECUTION),
                            eq(9L),
                            anyString(),
                            eq(2L),
                            eq("학습"));
        }

        @Test
        @DisplayName("IN_PROGRESS가 없으면 자동 완료를 건너뛴다")
        void autoCompletePastDay_noInProgress() {
            // given
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
            when(missionExecutionService.markMissedExecutions()).thenReturn(0);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of());

            // when
            scheduler.generateDailyInstances();

            // then
            verify(dailyMissionInstanceService, never())
                    .completeInstance(any(), anyString(), isNull(), anyBoolean());
            verify(missionExecutionService, never())
                    .completeExecution(any(Long.class), anyString(), isNull(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("자정 자동 완료 추가 분기 테스트 (startedAt null, 최대수행시간 미경과, 폴백 분기)")
    class MidnightAutoCompleteBranchCoverageTest {

        private void stubRestEmpty() {
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
            when(missionExecutionService.markMissedExecutions()).thenReturn(0);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of());
        }

        private DailyMissionInstance inProgressInstance(
                MissionParticipant p, LocalDateTime startedAt) {
            DailyMissionInstance instance =
                    DailyMissionInstance.createFrom(p, today().minusDays(1));
            setId(instance, 100L);
            instance.setStatus(ExecutionStatus.IN_PROGRESS);
            instance.setStartedAt(startedAt);
            return instance;
        }

        private MissionExecution inProgressExecution(
                MissionParticipant p, LocalDateTime startedAt) {
            MissionExecution execution =
                    MissionExecution.builder()
                            .participant(p)
                            .executionDate(today().minusDays(1))
                            .status(ExecutionStatus.IN_PROGRESS)
                            .startedAt(startedAt)
                            .build();
            setId(execution, 200L);
            return execution;
        }

        @Test
        @DisplayName("startedAt 이 null 인 고정 인스턴스는 경과시간 판정 없이 Saga 자동 완료를 시도한다")
        void instance_startedAtNull_completesViaSaga() {
            // given
            when(missionExecutionProperties.getMaxExecutionMinutes()).thenReturn(240);
            DailyMissionInstance instance = inProgressInstance(participant1, null);
            stubRestEmpty();
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(instance));

            // when
            scheduler.generateDailyInstances();

            // then
            verify(dailyMissionInstanceService)
                    .completeInstance(eq(100L), eq(USER_ID_1), isNull(), eq(false));
        }

        @Test
        @DisplayName("최대 수행시간 미경과 고정 인스턴스는 자정 자동 완료를 건너뛴다")
        void instance_underMaxExecutionMinutes_skipped() {
            // given
            when(missionExecutionProperties.getMaxExecutionMinutes()).thenReturn(240);
            DailyMissionInstance instance =
                    inProgressInstance(participant1, LocalDateTime.now().minusMinutes(30));
            stubRestEmpty();
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(instance));

            // when
            scheduler.generateDailyInstances();

            // then
            verify(dailyMissionInstanceService, never())
                    .completeInstance(any(), anyString(), isNull(), anyBoolean());
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("Saga 실패 + startedAt null 이면 폴백 완료도 불가하여 저장하지 않는다")
        void instance_sagaFails_fallbackReturnsFalse_noSave() {
            // given
            when(missionExecutionProperties.getMaxExecutionMinutes()).thenReturn(240);
            DailyMissionInstance instance = inProgressInstance(participant1, null);
            stubRestEmpty();
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(instance));
            doThrow(new RuntimeException("Saga 실패"))
                    .when(dailyMissionInstanceService)
                    .completeInstance(any(), anyString(), isNull(), anyBoolean());

            // when
            scheduler.generateDailyInstances();

            // then
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
            verify(instanceRepository, never()).save(any());
            verify(gamificationQueryFacade, never())
                    .addExperience(
                            any(),
                            org.mockito.ArgumentMatchers.anyInt(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any());
        }

        @Test
        @DisplayName("폴백 완료 시 baseExp 가 0 이면 gamification EXP 를 지급하지 않는다")
        void instance_fallback_baseExpZero_skipsGrant() {
            // given
            when(missionExecutionProperties.getBaseExp()).thenReturn(0);
            DailyMissionInstance instance =
                    inProgressInstance(participant1, LocalDateTime.now().minusHours(5));
            stubRestEmpty();
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(instance));
            doThrow(new RuntimeException("Saga 실패"))
                    .when(dailyMissionInstanceService)
                    .completeInstance(any(), anyString(), isNull(), anyBoolean());

            // when
            scheduler.generateDailyInstances();

            // then
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(instance.getExpEarned()).isEqualTo(0);
            verify(instanceRepository).save(instance);
            verify(gamificationQueryFacade, never())
                    .addExperience(
                            any(),
                            org.mockito.ArgumentMatchers.anyInt(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any());
        }

        @Test
        @DisplayName("폴백 완료 시 participant.mission 이 null 이면 gamification EXP 를 지급하지 않는다")
        void instance_fallback_missionNull_skipsGrant() {
            // given
            MissionParticipant noMissionParticipant =
                    MissionParticipant.builder()
                            .mission(null)
                            .userId(USER_ID_1)
                            .status(ParticipantStatus.ACCEPTED)
                            .build();
            setId(noMissionParticipant, 77L);
            DailyMissionInstance instance =
                    DailyMissionInstance.builder()
                            .participant(noMissionParticipant)
                            .instanceDate(today().minusDays(1))
                            .sequenceNumber(1)
                            .missionTitle("미션 없는 인스턴스")
                            .status(ExecutionStatus.IN_PROGRESS)
                            .startedAt(LocalDateTime.now().minusHours(5))
                            .expEarned(0)
                            .completionCount(0)
                            .totalExpEarned(0)
                            .build();
            setId(instance, 101L);
            stubRestEmpty();
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(instance));
            doThrow(new RuntimeException("Saga 실패"))
                    .when(dailyMissionInstanceService)
                    .completeInstance(any(), anyString(), isNull(), anyBoolean());

            // when
            scheduler.generateDailyInstances();

            // then
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            verify(instanceRepository).save(instance);
            verify(gamificationQueryFacade, never())
                    .addExperience(
                            any(),
                            org.mockito.ArgumentMatchers.anyInt(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any());
        }

        @Test
        @DisplayName("폴백 EXP 지급 실패는 로그만 남기고 카운트는 유지된다")
        void instance_fallback_grantThrows_swallowed() {
            // given
            DailyMissionInstance instance =
                    inProgressInstance(participant1, LocalDateTime.now().minusHours(5));
            stubRestEmpty();
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(instance));
            doThrow(new RuntimeException("Saga 실패"))
                    .when(dailyMissionInstanceService)
                    .completeInstance(any(), anyString(), isNull(), anyBoolean());
            when(gamificationQueryFacade.addExperience(
                            any(),
                            org.mockito.ArgumentMatchers.anyInt(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any()))
                    .thenThrow(new RuntimeException("gamification down"));

            // when & then
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> scheduler.generateDailyInstances());
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        }

        @Test
        @DisplayName("startedAt 이 null 인 일반 미션 실행은 경과시간 판정 없이 Saga 자동 완료를 시도한다")
        void execution_startedAtNull_completesViaSaga() {
            // given
            when(missionExecutionProperties.getMaxExecutionMinutes()).thenReturn(240);
            MissionExecution execution = inProgressExecution(participant1, null);
            stubRestEmpty();
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(execution));

            // when
            scheduler.generateDailyInstances();

            // then
            verify(missionExecutionService)
                    .completeExecution(eq(200L), eq(USER_ID_1), isNull(), eq(false));
        }

        @Test
        @DisplayName("최대 수행시간 미경과 일반 미션 실행은 자정 자동 완료를 건너뛴다")
        void execution_underMaxExecutionMinutes_skipped() {
            // given
            when(missionExecutionProperties.getMaxExecutionMinutes()).thenReturn(240);
            MissionExecution execution =
                    inProgressExecution(participant1, LocalDateTime.now().minusMinutes(30));
            stubRestEmpty();
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(execution));

            // when
            scheduler.generateDailyInstances();

            // then
            verify(missionExecutionService, never())
                    .completeExecution(any(Long.class), anyString(), isNull(), anyBoolean());
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("일반 미션 Saga 실패 + startedAt null 이면 폴백 완료도 불가하여 저장하지 않는다")
        void execution_sagaFails_fallbackReturnsFalse_noSave() {
            // given
            when(missionExecutionProperties.getMaxExecutionMinutes()).thenReturn(240);
            MissionExecution execution = inProgressExecution(participant1, null);
            stubRestEmpty();
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(execution));
            doThrow(new RuntimeException("Saga 실패"))
                    .when(missionExecutionService)
                    .completeExecution(any(Long.class), anyString(), isNull(), anyBoolean());

            // when
            scheduler.generateDailyInstances();

            // then
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
            verify(executionRepository, never()).save(any());
            verify(participantRepository, never()).save(any());
        }

        @Test
        @DisplayName("고정(isPinned) 미션의 일반 실행 폴백 시 participant 상태를 바꾸지 않는다")
        void execution_fallback_pinnedMission_participantNotUpdated() {
            // given: participant1.mission 은 isPinned=true
            MissionExecution execution =
                    inProgressExecution(participant1, LocalDateTime.now().minusHours(5));
            stubRestEmpty();
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(execution));
            doThrow(new RuntimeException("Saga 실패"))
                    .when(missionExecutionService)
                    .completeExecution(any(Long.class), anyString(), isNull(), anyBoolean());

            // when
            scheduler.generateDailyInstances();

            // then
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(participant1.getStatus()).isEqualTo(ParticipantStatus.ACCEPTED);
            verify(participantRepository, never()).save(any());
            verify(gamificationQueryFacade)
                    .addExperience(
                            eq(USER_ID_1),
                            eq(10),
                            eq(io.pinkspider.global.enums.ExpSourceType.MISSION_EXECUTION),
                            eq(1L),
                            anyString(),
                            eq(1L),
                            eq("운동"));
        }

        @Test
        @DisplayName("이미 COMPLETED 인 participant 는 폴백 시 상태를 다시 바꾸지 않는다")
        void execution_fallback_participantAlreadyCompleted_notUpdated() {
            // given
            Mission regularMission =
                    Mission.builder()
                            .title("일반 미션")
                            .creatorId(USER_ID_1)
                            .status(MissionStatus.IN_PROGRESS)
                            .visibility(MissionVisibility.PRIVATE)
                            .type(MissionType.PERSONAL)
                            .categoryId(2L)
                            .categoryName("학습")
                            .isPinned(false)
                            .build();
            setId(regularMission, 9L);
            MissionParticipant completedParticipant =
                    MissionParticipant.builder()
                            .mission(regularMission)
                            .userId(USER_ID_1)
                            .status(ParticipantStatus.COMPLETED)
                            .build();
            setId(completedParticipant, 9L);
            MissionExecution execution =
                    inProgressExecution(completedParticipant, LocalDateTime.now().minusHours(5));
            stubRestEmpty();
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(execution));
            doThrow(new RuntimeException("Saga 실패"))
                    .when(missionExecutionService)
                    .completeExecution(any(Long.class), anyString(), isNull(), anyBoolean());

            // when
            scheduler.generateDailyInstances();

            // then
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(completedParticipant.getCompletedAt()).isNull();
            verify(participantRepository, never()).save(any());
        }

        @Test
        @DisplayName("일반 미션 폴백 완료 시 baseExp 가 0 이면 gamification EXP 를 지급하지 않는다")
        void execution_fallback_baseExpZero_skipsGrant() {
            // given
            when(missionExecutionProperties.getBaseExp()).thenReturn(0);
            MissionExecution execution =
                    inProgressExecution(participant1, LocalDateTime.now().minusHours(5));
            stubRestEmpty();
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(execution));
            doThrow(new RuntimeException("Saga 실패"))
                    .when(missionExecutionService)
                    .completeExecution(any(Long.class), anyString(), isNull(), anyBoolean());

            // when
            scheduler.generateDailyInstances();

            // then
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            verify(gamificationQueryFacade, never())
                    .addExperience(
                            any(),
                            org.mockito.ArgumentMatchers.anyInt(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any());
        }

        @Test
        @DisplayName("일반 미션 폴백 중 participant.mission 이 null 이면 폴백 예외를 잡고 계속 진행한다")
        void execution_fallback_missionNull_swallowed() {
            // given
            MissionParticipant noMissionParticipant =
                    MissionParticipant.builder()
                            .mission(null)
                            .userId(USER_ID_1)
                            .status(ParticipantStatus.ACCEPTED)
                            .build();
            setId(noMissionParticipant, 78L);
            MissionExecution execution =
                    inProgressExecution(noMissionParticipant, LocalDateTime.now().minusHours(5));
            stubRestEmpty();
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(execution));
            doThrow(new RuntimeException("Saga 실패"))
                    .when(missionExecutionService)
                    .completeExecution(any(Long.class), anyString(), isNull(), anyBoolean());

            // when & then
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> scheduler.generateDailyInstances());
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            verify(executionRepository).save(execution);
            verify(gamificationQueryFacade, never())
                    .addExperience(
                            any(),
                            org.mockito.ArgumentMatchers.anyInt(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any());
        }
    }

    @Nested
    @DisplayName("createTodayInstances 배치 분기 테스트")
    class CreateTodayInstancesBatchTest {

        private void stubMidnightEmpty() {
            when(instanceRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(executionRepository.findInProgressBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
            when(missionExecutionService.markMissedExecutions()).thenReturn(0);
        }

        private List<MissionParticipant> participants(int count) {
            List<MissionParticipant> list = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                MissionParticipant p =
                        MissionParticipant.builder()
                                .mission(mission)
                                .userId("user-" + i)
                                .status(ParticipantStatus.ACCEPTED)
                                .build();
                setId(p, (long) (1000 + i));
                list.add(p);
            }
            return list;
        }

        @Test
        @DisplayName("참여자가 정확히 배치 크기(100)면 루프 안에서 한 번만 저장하고 최종 저장은 생략한다")
        void createTodayInstances_exactBatchSize_savesOnceInLoop() {
            // given
            stubMidnightEmpty();
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(participants(100));
            when(instanceRepository.existsByParticipantIdAndInstanceDate(
                            any(), any(LocalDate.class)))
                    .thenReturn(false);
            when(instanceRepository.saveAll(any()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            scheduler.generateDailyInstances();

            // then
            verify(instanceRepository, times(1)).saveAll(any());
        }

        @Test
        @DisplayName("참여자가 배치 크기를 넘으면 루프 저장 + 남은 인스턴스 최종 저장으로 두 번 저장한다")
        void createTodayInstances_overBatchSize_savesTwice() {
            // given
            stubMidnightEmpty();
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(participants(101));
            when(instanceRepository.existsByParticipantIdAndInstanceDate(
                            any(), any(LocalDate.class)))
                    .thenReturn(false);
            when(instanceRepository.saveAll(any()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            scheduler.generateDailyInstances();

            // then
            verify(instanceRepository, times(2)).saveAll(any());
        }

        @Test
        @DisplayName("모든 참여자가 이미 오늘 인스턴스를 가지면 저장하지 않는다")
        void createTodayInstances_allExisting_noSave() {
            // given
            stubMidnightEmpty();
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of(participant1, participant2));
            when(instanceRepository.existsByParticipantIdAndInstanceDate(
                            any(), any(LocalDate.class)))
                    .thenReturn(true);

            // when
            scheduler.generateDailyInstances();

            // then
            verify(instanceRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("createOrGetTodayInstance 테스트")
    class CreateOrGetTodayInstanceTest {

        @Test
        @DisplayName("PENDING 인스턴스가 없으면 새로 생성한다")
        void createOrGetTodayInstance_creates() {
            // given
            LocalDate today = today();
            when(instanceRepository.findPendingByParticipantIdAndDate(eq(1L), eq(today)))
                    .thenReturn(List.of());
            when(instanceRepository.findMaxSequenceNumber(eq(1L), eq(today))).thenReturn(0);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(
                            invocation -> {
                                DailyMissionInstance instance = invocation.getArgument(0);
                                setId(instance, 100L);
                                return instance;
                            });

            // when
            DailyMissionInstance result = scheduler.createOrGetTodayInstance(participant1);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getInstanceDate()).isEqualTo(today);
            assertThat(result.getMissionTitle()).isEqualTo("매일 30분 운동");
            assertThat(result.getSequenceNumber()).isEqualTo(1);
            verify(instanceRepository).save(any(DailyMissionInstance.class));
        }

        @Test
        @DisplayName("PENDING 인스턴스가 있으면 기존 인스턴스를 반환한다")
        void createOrGetTodayInstance_returnsExisting() {
            // given
            LocalDate today = today();
            DailyMissionInstance existingInstance =
                    DailyMissionInstance.createFrom(participant1, today);
            setId(existingInstance, 100L);

            when(instanceRepository.findPendingByParticipantIdAndDate(eq(1L), eq(today)))
                    .thenReturn(List.of(existingInstance));

            // when
            DailyMissionInstance result = scheduler.createOrGetTodayInstance(participant1);

            // then
            assertThat(result).isEqualTo(existingInstance);
            verify(instanceRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("generateInstancesForDate 테스트 (관리자 수동 실행)")
    class GenerateInstancesForDateTest {

        @Test
        @DisplayName("특정 날짜의 인스턴스를 수동 생성한다")
        void generateInstancesForDate_success() {
            // given
            LocalDate targetDate = LocalDate.of(2026, 1, 15);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of(participant1, participant2));
            when(instanceRepository.existsByParticipantIdAndInstanceDate(eq(1L), eq(targetDate)))
                    .thenReturn(false);
            when(instanceRepository.existsByParticipantIdAndInstanceDate(eq(2L), eq(targetDate)))
                    .thenReturn(false);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            int createdCount = scheduler.generateInstancesForDate(targetDate);

            // then
            assertThat(createdCount).isEqualTo(2);
            verify(instanceRepository, times(2)).save(any(DailyMissionInstance.class));
        }

        @Test
        @DisplayName("이미 존재하는 인스턴스는 생성하지 않는다")
        void generateInstancesForDate_skipsExisting() {
            // given
            LocalDate targetDate = LocalDate.of(2026, 1, 15);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of(participant1, participant2));
            when(instanceRepository.existsByParticipantIdAndInstanceDate(eq(1L), eq(targetDate)))
                    .thenReturn(true); // 이미 존재
            when(instanceRepository.existsByParticipantIdAndInstanceDate(eq(2L), eq(targetDate)))
                    .thenReturn(false);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            int createdCount = scheduler.generateInstancesForDate(targetDate);

            // then
            assertThat(createdCount).isEqualTo(1);
            verify(instanceRepository, times(1)).save(any(DailyMissionInstance.class));
        }

        @Test
        @DisplayName("참여자가 없으면 0을 반환한다")
        void generateInstancesForDate_noParticipants() {
            // given
            LocalDate targetDate = LocalDate.of(2026, 1, 15);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                    .thenReturn(List.of());

            // when
            int createdCount = scheduler.generateInstancesForDate(targetDate);

            // then
            assertThat(createdCount).isEqualTo(0);
            verify(instanceRepository, never()).save(any());
        }
    }
}
