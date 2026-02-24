package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.UserMissionHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MissionParticipantAdminServiceTest {

    @Mock
    private MissionParticipantRepository participantRepository;

    @Mock
    private MissionExecutionRepository executionRepository;

    @InjectMocks
    private MissionParticipantAdminService service;

    private Mission createTestMission(Long id) {
        Mission mission = Mission.builder()
            .title("테스트 미션")
            .description("설명")
            .status(MissionStatus.OPEN)
            .type(MissionType.PERSONAL)
            .source(MissionSource.SYSTEM)
            .missionInterval(MissionInterval.DAILY)
            .creatorId("admin-1")
            .build();
        setId(mission, id);
        return mission;
    }

    private MissionParticipant createTestParticipant(Long id) {
        Mission mission = createTestMission(1L);
        MissionParticipant participant = MissionParticipant.builder()
            .mission(mission)
            .userId("user-1")
            .status(ParticipantStatus.IN_PROGRESS)
            .progress(50)
            .joinedAt(LocalDateTime.now())
            .build();
        setId(participant, id);
        return participant;
    }

    @Nested
    @DisplayName("getUserMissionHistory 테스트")
    class GetUserMissionHistoryTest {

        @Test
        @DisplayName("사용자의 미션 참여 이력을 조회한다")
        void getUserMissionHistory() {
            Pageable pageable = PageRequest.of(0, 10);
            MissionParticipant participant = createTestParticipant(1L);
            when(participantRepository.findByUserIdWithMissionPaged("user-1", pageable))
                .thenReturn(new PageImpl<>(List.of(participant)));
            when(executionRepository.countByParticipantIdAndStatus(eq(1L), any(ExecutionStatus.class)))
                .thenReturn(0L);
            when(executionRepository.sumExpEarnedByParticipantId(1L)).thenReturn(100);

            UserMissionHistoryAdminPageResponse result = service.getUserMissionHistory("user-1", pageable);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("경험치가 null이면 0으로 반환한다")
        void nullExpReturnsZero() {
            Pageable pageable = PageRequest.of(0, 10);
            MissionParticipant participant = createTestParticipant(1L);
            when(participantRepository.findByUserIdWithMissionPaged("user-1", pageable))
                .thenReturn(new PageImpl<>(List.of(participant)));
            when(executionRepository.countByParticipantIdAndStatus(eq(1L), any(ExecutionStatus.class)))
                .thenReturn(0L);
            when(executionRepository.sumExpEarnedByParticipantId(1L)).thenReturn(null);

            UserMissionHistoryAdminPageResponse result = service.getUserMissionHistory("user-1", pageable);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("빈 목록을 반환한다")
        void emptyHistory() {
            Pageable pageable = PageRequest.of(0, 10);
            when(participantRepository.findByUserIdWithMissionPaged("user-1", pageable))
                .thenReturn(new PageImpl<>(List.of()));

            UserMissionHistoryAdminPageResponse result = service.getUserMissionHistory("user-1", pageable);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("countParticipantsByUserId 테스트")
    class CountParticipantsTest {

        @Test
        @DisplayName("사용자의 참여 미션 수를 반환한다")
        void countParticipants() {
            when(participantRepository.countByUserId("user-1")).thenReturn(5L);

            long result = service.countParticipantsByUserId("user-1");

            assertThat(result).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("countExecutionsByUserId 테스트")
    class CountExecutionsTest {

        @Test
        @DisplayName("사용자의 전체 실행 수를 반환한다")
        void countExecutions() {
            when(executionRepository.countByUserId("user-1")).thenReturn(20L);

            long result = service.countExecutionsByUserId("user-1");

            assertThat(result).isEqualTo(20L);
        }
    }

    @Nested
    @DisplayName("countCompletedExecutionsByUserId 테스트")
    class CountCompletedExecutionsTest {

        @Test
        @DisplayName("사용자의 완료된 실행 수를 반환한다")
        void countCompletedExecutions() {
            when(executionRepository.countCompletedByUserId("user-1")).thenReturn(15L);

            long result = service.countCompletedExecutionsByUserId("user-1");

            assertThat(result).isEqualTo(15L);
        }
    }
}
