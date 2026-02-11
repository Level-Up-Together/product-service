package io.pinkspider.leveluptogethermvp.missionservice.scheduler;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyMissionInstanceScheduler 테스트")
class DailyMissionInstanceSchedulerTest {

    @Mock
    private DailyMissionInstanceRepository instanceRepository;

    @Mock
    private MissionParticipantRepository participantRepository;

    @InjectMocks
    private DailyMissionInstanceScheduler scheduler;

    @Captor
    private ArgumentCaptor<List<DailyMissionInstance>> instanceListCaptor;

    private static final String USER_ID_1 = "user-1";
    private static final String USER_ID_2 = "user-2";

    private Mission mission;
    private MissionParticipant participant1;
    private MissionParticipant participant2;

    @BeforeEach
    void setUp() {
        mission = Mission.builder()
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

        participant1 = MissionParticipant.builder()
            .mission(mission)
            .userId(USER_ID_1)
            .status(ParticipantStatus.ACCEPTED)
            .build();
        setId(participant1, 1L);

        participant2 = MissionParticipant.builder()
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
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                .thenReturn(List.of(participant1, participant2));
            when(instanceRepository.existsByParticipantIdAndInstanceDate(eq(1L), any(LocalDate.class)))
                .thenReturn(false);
            when(instanceRepository.existsByParticipantIdAndInstanceDate(eq(2L), any(LocalDate.class)))
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
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
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
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(0);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                .thenReturn(List.of(participant1, participant2));
            when(instanceRepository.existsByParticipantIdAndInstanceDate(eq(1L), any(LocalDate.class)))
                .thenReturn(true);  // participant1은 이미 존재
            when(instanceRepository.existsByParticipantIdAndInstanceDate(eq(2L), any(LocalDate.class)))
                .thenReturn(false);

            // when
            scheduler.generateDailyInstances();

            // then
            verify(instanceRepository).saveAll(instanceListCaptor.capture());

            List<DailyMissionInstance> savedInstances = instanceListCaptor.getValue();
            assertThat(savedInstances).hasSize(1);  // participant2만 생성
            assertThat(savedInstances.get(0).getParticipant().getUserId()).isEqualTo(USER_ID_2);
        }

        @Test
        @DisplayName("미완료 인스턴스를 MISSED 처리한다")
        void generateDailyInstances_marksMissed() {
            // given
            when(instanceRepository.markMissedInstances(any(LocalDate.class))).thenReturn(5);
            when(participantRepository.findAllActivePinnedMissionParticipants())
                .thenReturn(List.of());

            // when
            scheduler.generateDailyInstances();

            // then
            verify(instanceRepository).markMissedInstances(any(LocalDate.class));
        }
    }

    @Nested
    @DisplayName("createOrGetTodayInstance 테스트")
    class CreateOrGetTodayInstanceTest {

        @Test
        @DisplayName("PENDING 인스턴스가 없으면 새로 생성한다")
        void createOrGetTodayInstance_creates() {
            // given
            LocalDate today = LocalDate.now();
            when(instanceRepository.findPendingByParticipantIdAndDate(eq(1L), eq(today)))
                .thenReturn(List.of());
            when(instanceRepository.findMaxSequenceNumber(eq(1L), eq(today)))
                .thenReturn(0);
            when(instanceRepository.save(any(DailyMissionInstance.class)))
                .thenAnswer(invocation -> {
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
            LocalDate today = LocalDate.now();
            DailyMissionInstance existingInstance = DailyMissionInstance.createFrom(participant1, today);
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
                .thenReturn(true);  // 이미 존재
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
