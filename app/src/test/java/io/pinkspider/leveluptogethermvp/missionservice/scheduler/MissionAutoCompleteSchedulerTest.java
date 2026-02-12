package io.pinkspider.leveluptogethermvp.missionservice.scheduler;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @InjectMocks
    private MissionAutoCompleteScheduler scheduler;

    private static final String USER_ID = "user-1";

    private Mission mission;
    private MissionParticipant participant;

    @BeforeEach
    void setUp() {
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
            assertThat(execution.getExpEarned()).isEqualTo(120); // 최대 2시간 = 120분
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
            assertThat(instance.getExpEarned()).isEqualTo(120); // 최대 2시간 = 120분
            assertThat(instance.getCompletionCount()).isEqualTo(1);
            assertThat(instance.getTotalExpEarned()).isEqualTo(120);
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
            boolean result = execution.autoCompleteIfExpired();

            // then
            assertThat(result).isTrue();
            assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(execution.getIsAutoCompleted()).isTrue();
            assertThat(execution.getExpEarned()).isEqualTo(120);
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
            boolean result = execution.autoCompleteIfExpired();

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
            boolean result = instance.autoCompleteIfExpired();

            // then
            assertThat(result).isTrue();
            assertThat(instance.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(instance.getIsAutoCompleted()).isTrue();
            assertThat(instance.getExpEarned()).isEqualTo(120);
            assertThat(instance.getCompletionCount()).isEqualTo(1);
        }
    }
}
