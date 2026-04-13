package io.pinkspider.leveluptogethermvp.missionservice.scheduler;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;

import io.pinkspider.leveluptogethermvp.missionservice.config.MissionExecutionProperties;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.application.DailyMissionInstanceService;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionExecutionService;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import org.springframework.context.ApplicationEventPublisher;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
@DisplayName("MissionAutoCompleteScheduler 테스트")
class MissionAutoCompleteSchedulerTest {

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private DailyMissionInstanceRepository instanceRepository;

    @Mock
    private DailyMissionInstanceService dailyMissionInstanceService;

    @Mock
    private MissionExecutionService missionExecutionService;

    @Mock
    private MissionExecutionProperties missionExecutionProperties;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MissionAutoCompleteScheduler scheduler;

    private static final String USER_ID = "user-1";

    private Mission mission;
    private MissionParticipant participant;

    @BeforeEach
    void setUp() {
        when(missionExecutionProperties.getBaseExp()).thenReturn(10);
        when(missionExecutionProperties.getWarningMinutesBeforeAutoEnd()).thenReturn(10);

        mission = Mission.builder()
            .title("매일 30분 운동")
            .description("매일 30분씩 운동하기")
            .creatorId(USER_ID)
            .status(MissionStatus.IN_PROGRESS)
            .visibility(MissionVisibility.PRIVATE)
            .type(MissionType.PERSONAL)
            .categoryId(1L)
            .categoryName("운동")
            .expPerCompletion(50)
            .isPinned(true)
            .build();
        setId(mission, 1L);

        participant = MissionParticipant.builder()
            .mission(mission)
            .userId(USER_ID)
            .status(ParticipantStatus.ACCEPTED)
            .build();
        setId(participant, 1L);
    }

    @Nested
    @DisplayName("MissionExecution 자동 종료 테스트")
    class MissionExecutionAutoCompleteTest {

        @Test
        @DisplayName("2시간 초과된 MissionExecution이 자동 종료된다")
        void autoCompleteExpiredMissionExecution() {
            // given
            MissionExecution execution = MissionExecution.builder()
                .participant(participant)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusHours(3)) // 3시간 전 시작
                .build();
            setId(execution, 1L);

            when(executionRepository.findInProgressWarningExecutions(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
            when(instanceRepository.findInProgressWarningInstances(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration())
                .thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration())
                .thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any(LocalDateTime.class)))
                .thenReturn(List.of(execution));
            when(instanceRepository.findExpiredInProgressInstances(any(LocalDateTime.class)))
                .thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(execution.getIsAutoCompleted()).isTrue();
            assertThat(execution.getExpEarned()).isEqualTo(10); // 기본 경험치
        }

        @Test
        @DisplayName("2시간 미만 MissionExecution은 자동 종료되지 않는다")
        void doNotAutoCompleteNonExpiredMissionExecution() {
            // given
            MissionExecution execution = MissionExecution.builder()
                .participant(participant)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusHours(1)) // 1시간 전 시작
                .build();
            setId(execution, 1L);

            // 2시간 초과된 것만 조회되므로 빈 리스트 반환
            when(executionRepository.findInProgressWarningExecutions(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
            when(instanceRepository.findInProgressWarningInstances(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration())
                .thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration())
                .thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any(LocalDateTime.class)))
                .thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any(LocalDateTime.class)))
                .thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
            assertThat(execution.getIsAutoCompleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("DailyMissionInstance 자동 종료 테스트")
    class DailyMissionInstanceAutoCompleteTest {

        @Test
        @DisplayName("2시간 초과된 DailyMissionInstance가 자동 종료된다")
        void autoCompleteExpiredDailyMissionInstance() {
            // given
            DailyMissionInstance instance = DailyMissionInstance.builder()
                .participant(participant)
                .instanceDate(LocalDate.now())
                .sequenceNumber(1)
                .missionTitle(mission.getTitle())
                .missionDescription(mission.getDescription())
                .categoryName("운동")
                .categoryId(1L)
                .expPerCompletion(mission.getExpPerCompletion())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusHours(3)) // 3시간 전 시작
                .expEarned(0)
                .completionCount(0)
                .totalExpEarned(0)
                .isSharedToFeed(false)
                .isAutoCompleted(false)
                .build();
            setId(instance, 1L);

            when(executionRepository.findInProgressWarningExecutions(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
            when(instanceRepository.findInProgressWarningInstances(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration())
                .thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration())
                .thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any(LocalDateTime.class)))
                .thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any(LocalDateTime.class)))
                .thenReturn(List.of(instance));

            // when
            scheduler.autoCompleteExpiredMissions();

            // then
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(instance.getIsAutoCompleted()).isTrue();
            assertThat(instance.getExpEarned()).isEqualTo(10); // 기본 경험치
            assertThat(instance.getCompletionCount()).isEqualTo(1);
            assertThat(instance.getTotalExpEarned()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("sendAutoEndWarnings 분기 테스트")
    class SendAutoEndWarningsTest {

        @Test
        @DisplayName("warningMinutesAfterStart가 null이면 경고를 발송하지 않는다")
        void sendAutoEndWarnings_warningPointsNull_returnsZero() {
            // given
            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(null);
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then (eventPublisher.publishEvent가 호출되지 않음)
            org.mockito.Mockito.verify(eventPublisher, org.mockito.Mockito.never())
                .publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("warningMinutesAfterStart가 빈 리스트이면 경고를 발송하지 않는다")
        void sendAutoEndWarnings_emptyWarningPoints_returnsZero() {
            // given
            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then
            org.mockito.Mockito.verify(eventPublisher, org.mockito.Mockito.never())
                .publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("일반 미션에 targetDurationMinutes가 있으면 경고 알림을 스킵한다")
        void sendAutoEndWarnings_executionWithTargetDuration_skips() {
            // given
            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of(60));

            // targetDurationMinutes 설정된 미션
            Mission missionWithTarget = Mission.builder()
                .title("목표시간 미션")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .creatorId(USER_ID)
                .targetDurationMinutes(30)
                .isPinned(false)
                .expPerCompletion(50)
                .categoryId(1L)
                .categoryName("운동")
                .build();
            setId(missionWithTarget, 10L);

            MissionParticipant participantWithTarget = MissionParticipant.builder()
                .mission(missionWithTarget)
                .userId(USER_ID)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(participantWithTarget, 10L);

            MissionExecution executionWithTarget = MissionExecution.builder()
                .participant(participantWithTarget)
                .executionDate(java.time.LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(65))
                .build();
            setId(executionWithTarget, 10L);

            when(executionRepository.findInProgressWarningExecutions(any(), any()))
                .thenReturn(List.of(executionWithTarget));
            when(instanceRepository.findInProgressWarningInstances(any(), any()))
                .thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then - targetDurationMinutes가 있으므로 경고 알림 skip
            org.mockito.Mockito.verify(eventPublisher, org.mockito.Mockito.never())
                .publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("고정 미션에 targetDurationMinutes > 0이면 경고 알림을 스킵한다")
        void sendAutoEndWarnings_instanceWithTargetDuration_skips() {
            // given
            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of(60));

            DailyMissionInstance instanceWithTarget = DailyMissionInstance.builder()
                .participant(participant)
                .instanceDate(java.time.LocalDate.now())
                .sequenceNumber(1)
                .missionTitle("목표시간 고정미션")
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(65))
                .targetDurationMinutes(30)  // targetDuration > 0 → skip
                .completionCount(0)
                .totalExpEarned(0)
                .isAutoCompleted(false)
                .build();
            setId(instanceWithTarget, 10L);

            when(executionRepository.findInProgressWarningExecutions(any(), any())).thenReturn(List.of());
            when(instanceRepository.findInProgressWarningInstances(any(), any()))
                .thenReturn(List.of(instanceWithTarget));
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then - targetDurationMinutes > 0 → skip
            org.mockito.Mockito.verify(eventPublisher, org.mockito.Mockito.never())
                .publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("targetDurationMinutes = 0인 고정 미션은 경고 알림을 발송한다")
        void sendAutoEndWarnings_instanceWithZeroTargetDuration_sends() {
            // given
            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of(60));

            DailyMissionInstance instanceZeroTarget = DailyMissionInstance.builder()
                .participant(participant)
                .instanceDate(java.time.LocalDate.now())
                .sequenceNumber(1)
                .missionTitle("알림대상 고정미션")
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(65))
                .targetDurationMinutes(0)  // 0이면 스킵 안 함
                .completionCount(0)
                .totalExpEarned(0)
                .isAutoCompleted(false)
                .build();
            setId(instanceZeroTarget, 11L);

            when(executionRepository.findInProgressWarningExecutions(any(), any())).thenReturn(List.of());
            when(instanceRepository.findInProgressWarningInstances(any(), any()))
                .thenReturn(List.of(instanceZeroTarget));
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then - 경고 이벤트 발행됨
            org.mockito.Mockito.verify(eventPublisher)
                .publishEvent(any(io.pinkspider.global.event.MissionAutoEndWarningEvent.class));
        }
    }

    @Nested
    @DisplayName("autoCompleteTargetReachedInstances 분기 테스트")
    class AutoCompleteTargetReachedInstancesTest {

        @Test
        @DisplayName("고정 미션 경과 시간이 목표 시간에 도달하면 자동 종료 호출된다")
        void autoCompleteTargetReachedInstances_elapsed_greaterThanTarget() throws Exception {
            // given
            DailyMissionInstance instance = DailyMissionInstance.builder()
                .participant(participant)
                .instanceDate(java.time.LocalDate.now())
                .sequenceNumber(1)
                .missionTitle(mission.getTitle())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(35))  // 35분 경과 (UTC 기준)
                .targetDurationMinutes(30)  // 목표 30분 → 완료 조건
                .completionCount(0)
                .totalExpEarned(0)
                .isAutoCompleted(false)
                .build();
            setId(instance, 20L);

            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of(instance));
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then - Saga 경유 completeInstance 호출됨
            org.mockito.Mockito.verify(dailyMissionInstanceService)
                .completeInstance(instance.getId(), USER_ID, null, false);
        }

        @Test
        @DisplayName("고정 미션 경과 시간이 목표 시간에 미달하면 자동 종료 호출 안됨")
        void autoCompleteTargetReachedInstances_elapsed_lessThanTarget() throws Exception {
            // given
            DailyMissionInstance instance = DailyMissionInstance.builder()
                .participant(participant)
                .instanceDate(java.time.LocalDate.now())
                .sequenceNumber(1)
                .missionTitle(mission.getTitle())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(10))  // 10분만 경과
                .targetDurationMinutes(30)  // 목표 30분 → 아직 미달
                .completionCount(0)
                .totalExpEarned(0)
                .isAutoCompleted(false)
                .build();
            setId(instance, 21L);

            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of(instance));
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then - completeInstance 호출 안됨
            org.mockito.Mockito.verify(dailyMissionInstanceService, org.mockito.Mockito.never())
                .completeInstance(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        @DisplayName("고정 미션 자동 종료 실패 시 예외를 잡고 계속 진행한다")
        void autoCompleteTargetReachedInstances_completeThrows_continues() throws Exception {
            // given
            DailyMissionInstance instance = DailyMissionInstance.builder()
                .participant(participant)
                .instanceDate(java.time.LocalDate.now())
                .sequenceNumber(1)
                .missionTitle(mission.getTitle())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(35))
                .targetDurationMinutes(30)
                .completionCount(0)
                .totalExpEarned(0)
                .isAutoCompleted(false)
                .build();
            setId(instance, 22L);

            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of(instance));
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());
            org.mockito.Mockito.doThrow(new RuntimeException("완료 실패"))
                .when(dailyMissionInstanceService).completeInstance(instance.getId(), USER_ID, null, false);

            // when - 예외가 전파되지 않아야 함
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> scheduler.autoCompleteExpiredMissions()
            );
        }
    }

    @Nested
    @DisplayName("autoCompleteTargetReachedExecutions 분기 테스트")
    class AutoCompleteTargetReachedExecutionsTest {

        @Test
        @DisplayName("일반 미션 경과 시간이 목표 시간에 도달하면 Saga로 자동 종료된다")
        void autoCompleteTargetReachedExecutions_elapsed_greaterThanTarget() throws Exception {
            // given
            Mission missionWithTarget = Mission.builder()
                .title("목표 30분 미션")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .creatorId(USER_ID)
                .targetDurationMinutes(30)
                .isPinned(false)
                .expPerCompletion(50)
                .categoryId(1L)
                .categoryName("운동")
                .build();
            setId(missionWithTarget, 30L);

            MissionParticipant participantTarget = MissionParticipant.builder()
                .mission(missionWithTarget)
                .userId(USER_ID)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(participantTarget, 30L);

            MissionExecution execution = MissionExecution.builder()
                .participant(participantTarget)
                .executionDate(java.time.LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(35))  // 35분 경과 > 30분 목표 (UTC 기준)
                .build();
            setId(execution, 30L);

            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of(execution));
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then - Saga 경유 completeExecution 호출됨
            org.mockito.Mockito.verify(missionExecutionService)
                .completeExecution(execution.getId(), USER_ID, null, false);
        }

        @Test
        @DisplayName("일반 미션 targetDurationMinutes가 null이면 스킵된다")
        void autoCompleteTargetReachedExecutions_targetNull_skips() throws Exception {
            // given
            // participant.getMission().getTargetDurationMinutes() = null
            MissionExecution execution = MissionExecution.builder()
                .participant(participant)  // mission.targetDurationMinutes = null
                .executionDate(java.time.LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(35))
                .build();
            setId(execution, 31L);

            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of(execution));
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then - completeExecution 호출 안됨
            org.mockito.Mockito.verify(missionExecutionService, org.mockito.Mockito.never())
                .completeExecution(any(Long.class), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        @DisplayName("일반 미션 경과 시간이 목표 시간 미달이면 스킵된다")
        void autoCompleteTargetReachedExecutions_elapsed_lessThanTarget() throws Exception {
            // given
            Mission missionWithTarget = Mission.builder()
                .title("목표 60분 미션")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .creatorId(USER_ID)
                .targetDurationMinutes(60)
                .isPinned(false)
                .expPerCompletion(50)
                .categoryId(1L)
                .categoryName("운동")
                .build();
            setId(missionWithTarget, 32L);

            MissionParticipant participantTarget = MissionParticipant.builder()
                .mission(missionWithTarget)
                .userId(USER_ID)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(participantTarget, 32L);

            MissionExecution execution = MissionExecution.builder()
                .participant(participantTarget)
                .executionDate(java.time.LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(30))  // 30분 경과 < 60분 목표
                .build();
            setId(execution, 32L);

            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of(execution));
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of());
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then - completeExecution 호출 안됨
            org.mockito.Mockito.verify(missionExecutionService, org.mockito.Mockito.never())
                .completeExecution(any(Long.class), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        }
    }

    @Nested
    @DisplayName("autoCompleteExpiredExecutions 추가 분기 테스트")
    class AutoCompleteExpiredExecutionsExtraTest {

        @Test
        @DisplayName("targetDurationMinutes가 설정된 미션 execution은 스킵된다")
        void autoCompleteExpiredExecutions_withTargetDuration_skips() {
            // given
            Mission missionWithTarget = Mission.builder()
                .title("목표시간 미션")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .creatorId(USER_ID)
                .targetDurationMinutes(30)
                .isPinned(false)
                .expPerCompletion(50)
                .categoryId(1L)
                .categoryName("운동")
                .build();
            setId(missionWithTarget, 40L);

            MissionParticipant participantTarget = MissionParticipant.builder()
                .mission(missionWithTarget)
                .userId(USER_ID)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(participantTarget, 40L);

            MissionExecution execution = MissionExecution.builder()
                .participant(participantTarget)
                .executionDate(java.time.LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusHours(3))
                .build();
            setId(execution, 40L);

            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of(execution));
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then - targetDuration 있는 미션은 스킵되므로 status 유지
            org.assertj.core.api.Assertions.assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("participant.getMission()이 isPinned=true이면 participant status 업데이트 안됨")
        void autoCompleteExpiredExecutions_pinnedMission_participantNotUpdated() {
            // given: isPinned = true, participant.status != COMPLETED
            Mission pinnedMission = Mission.builder()
                .title("고정 미션")
                .status(MissionStatus.IN_PROGRESS)
                .visibility(MissionVisibility.PRIVATE)
                .type(MissionType.PERSONAL)
                .creatorId(USER_ID)
                .isPinned(true)
                .expPerCompletion(50)
                .categoryId(1L)
                .categoryName("운동")
                .build();
            setId(pinnedMission, 41L);

            MissionParticipant pinnedParticipant = MissionParticipant.builder()
                .mission(pinnedMission)
                .userId(USER_ID)
                .status(ParticipantStatus.IN_PROGRESS)
                .build();
            setId(pinnedParticipant, 41L);

            MissionExecution execution = MissionExecution.builder()
                .participant(pinnedParticipant)
                .executionDate(java.time.LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusHours(3))
                .build();
            setId(execution, 41L);

            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of(execution));
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then - autoComplete는 실행되지만 participant status 업데이트 안됨
            org.assertj.core.api.Assertions.assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            org.assertj.core.api.Assertions.assertThat(pinnedParticipant.getStatus()).isEqualTo(ParticipantStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("participant.status가 이미 COMPLETED이면 status를 변경하지 않는다")
        void autoCompleteExpiredExecutions_participantAlreadyCompleted_notUpdatedAgain() {
            // given
            MissionParticipant completedParticipant = MissionParticipant.builder()
                .mission(mission)
                .userId(USER_ID)
                .status(ParticipantStatus.COMPLETED)  // 이미 COMPLETED
                .build();
            setId(completedParticipant, 42L);

            MissionExecution execution = MissionExecution.builder()
                .participant(completedParticipant)
                .executionDate(java.time.LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusHours(3))
                .build();
            setId(execution, 42L);

            when(missionExecutionProperties.getWarningMinutesAfterStart()).thenReturn(List.of());
            when(instanceRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findInProgressWithTargetDuration()).thenReturn(List.of());
            when(executionRepository.findExpiredInProgressExecutions(any())).thenReturn(List.of(execution));
            when(instanceRepository.findExpiredInProgressInstances(any())).thenReturn(List.of());

            // when
            scheduler.autoCompleteExpiredMissions();

            // then - execution은 자동완료, participant는 이미 COMPLETED
            org.assertj.core.api.Assertions.assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            org.assertj.core.api.Assertions.assertThat(completedParticipant.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Entity autoCompleteIfExpired 메서드 테스트")
    class EntityAutoCompleteIfExpiredTest {

        @Test
        @DisplayName("MissionExecution.autoCompleteIfExpired - 2시간 초과 시 true 반환")
        void missionExecutionAutoCompleteIfExpired_returns_true_when_expired() {
            // given
            MissionExecution execution = MissionExecution.builder()
                .participant(participant)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(121)) // 121분 전 시작
                .build();

            // when
            boolean result = execution.autoCompleteIfExpired(10);

            // then
            assertThat(result).isTrue();
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(execution.getIsAutoCompleted()).isTrue();
            assertThat(execution.getExpEarned()).isEqualTo(10);
        }

        @Test
        @DisplayName("MissionExecution.autoCompleteIfExpired - 2시간 미만 시 false 반환")
        void missionExecutionAutoCompleteIfExpired_returns_false_when_not_expired() {
            // given
            MissionExecution execution = MissionExecution.builder()
                .participant(participant)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(60)) // 60분 전 시작
                .build();

            // when
            boolean result = execution.autoCompleteIfExpired(10);

            // then
            assertThat(result).isFalse();
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("MissionExecution.isExpired - 2시간 초과 시 true 반환")
        void missionExecutionIsExpired_returns_true_when_expired() {
            // given
            MissionExecution execution = MissionExecution.builder()
                .participant(participant)
                .executionDate(LocalDate.now())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(121))
                .build();

            // when & then
            assertThat(execution.isExpired()).isTrue();
        }

        @Test
        @DisplayName("DailyMissionInstance.autoCompleteIfExpired - 2시간 초과 시 true 반환")
        void dailyMissionInstanceAutoCompleteIfExpired_returns_true_when_expired() {
            // given
            DailyMissionInstance instance = DailyMissionInstance.builder()
                .participant(participant)
                .instanceDate(LocalDate.now())
                .sequenceNumber(1)
                .missionTitle(mission.getTitle())
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(121))
                .completionCount(0)
                .totalExpEarned(0)
                .isAutoCompleted(false)
                .build();

            // when
            boolean result = instance.autoCompleteIfExpired(10);

            // then
            assertThat(result).isTrue();
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(instance.getIsAutoCompleted()).isTrue();
            assertThat(instance.getExpEarned()).isEqualTo(10);
            assertThat(instance.getCompletionCount()).isEqualTo(1);
        }
    }
}
