package io.pinkspider.leveluptogethermvp.missionservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.UserMissionHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.UserMissionHistoryAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
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

    private Mission createMission(
        Long id, MissionType type, MissionSource source, String guildName, boolean deleted) {
        Mission mission = Mission.builder()
            .title("테스트 미션")
            .description("설명")
            .status(MissionStatus.OPEN)
            .type(type)
            .source(source)
            .missionInterval(MissionInterval.DAILY)
            .creatorId("admin-1")
            .guildName(guildName)
            .isDeleted(deleted)
            .build();
        setId(mission, id);
        return mission;
    }

    private MissionParticipant createParticipant(Long id, Mission mission, ParticipantStatus status) {
        MissionParticipant participant = MissionParticipant.builder()
            .mission(mission)
            .userId("user-1")
            .status(status)
            .progress(50)
            .joinedAt(LocalDateTime.of(2026, 6, 1, 9, 0, 0))
            .build();
        setId(participant, id);
        return participant;
    }

    @Nested
    @DisplayName("getUserMissionHistory 테스트")
    class GetUserMissionHistoryTest {

        @Test
        @DisplayName("IN_PROGRESS 참여자는 STARTED 상태, exp_earned=null 로 매핑된다")
        void mapsInProgressToStarted() {
            Pageable pageable = PageRequest.of(0, 10);
            MissionParticipant participant = createParticipant(1L,
                createMission(10L, MissionType.PERSONAL, MissionSource.USER, null, false),
                ParticipantStatus.IN_PROGRESS);
            when(participantRepository.searchUserMissionHistory(
                eq("user-1"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(participant)));
            when(executionRepository.sumExpEarnedByParticipantId(1L)).thenReturn(100);

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory("user-1", null, null, null, null, pageable);

            assertThat(result.content()).hasSize(1);
            UserMissionHistoryAdminResponse row = result.content().get(0);
            assertThat(row.status()).isEqualTo("STARTED");
            assertThat(row.missionType()).isEqualTo("PERSONAL");
            assertThat(row.guildName()).isNull();
            assertThat(row.expEarned()).isNull();
            assertThat(row.eventAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 0, 0));
        }

        @Test
        @DisplayName("COMPLETED 참여자는 COMPLETED 상태, exp_earned=합계 로 매핑된다")
        void mapsCompletedToCompleted() {
            Pageable pageable = PageRequest.of(0, 10);
            MissionParticipant participant = createParticipant(2L,
                createMission(11L, MissionType.PERSONAL, MissionSource.SYSTEM, null, false),
                ParticipantStatus.COMPLETED);
            when(participantRepository.searchUserMissionHistory(
                eq("user-1"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(participant)));
            when(executionRepository.sumExpEarnedByParticipantId(2L)).thenReturn(120);

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory("user-1", null, null, null, null, pageable);

            UserMissionHistoryAdminResponse row = result.content().get(0);
            assertThat(row.status()).isEqualTo("COMPLETED");
            assertThat(row.missionType()).isEqualTo("MISSION_BOOK");
            assertThat(row.expEarned()).isEqualTo(120);
        }

        @Test
        @DisplayName("FAILED 참여자도 COMPLETED 상태로 매핑된다")
        void mapsFailedToCompleted() {
            Pageable pageable = PageRequest.of(0, 10);
            MissionParticipant participant = createParticipant(3L,
                createMission(12L, MissionType.PERSONAL, MissionSource.USER, null, false),
                ParticipantStatus.FAILED);
            when(participantRepository.searchUserMissionHistory(
                eq("user-1"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(participant)));
            when(executionRepository.sumExpEarnedByParticipantId(3L)).thenReturn(null);

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory("user-1", null, null, null, null, pageable);

            UserMissionHistoryAdminResponse row = result.content().get(0);
            assertThat(row.status()).isEqualTo("COMPLETED");
            assertThat(row.expEarned()).isEqualTo(0);
        }

        @Test
        @DisplayName("WITHDRAWN 참여자는 DELETED 상태로 매핑된다")
        void mapsWithdrawnToDeleted() {
            Pageable pageable = PageRequest.of(0, 10);
            MissionParticipant participant = createParticipant(4L,
                createMission(13L, MissionType.PERSONAL, MissionSource.USER, null, false),
                ParticipantStatus.WITHDRAWN);
            when(participantRepository.searchUserMissionHistory(
                eq("user-1"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(participant)));

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory("user-1", null, null, null, null, pageable);

            UserMissionHistoryAdminResponse row = result.content().get(0);
            assertThat(row.status()).isEqualTo("DELETED");
            assertThat(row.expEarned()).isNull();
        }

        @Test
        @DisplayName("Mission.isDeleted=true 인 미션은 참여 상태와 무관하게 DELETED 로 매핑된다")
        void deletedMissionOverridesStatus() {
            Pageable pageable = PageRequest.of(0, 10);
            MissionParticipant participant = createParticipant(5L,
                createMission(14L, MissionType.PERSONAL, MissionSource.USER, null, true),
                ParticipantStatus.IN_PROGRESS);
            when(participantRepository.searchUserMissionHistory(
                eq("user-1"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(participant)));

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory("user-1", null, null, null, null, pageable);

            assertThat(result.content().get(0).status()).isEqualTo("DELETED");
        }

        @Test
        @DisplayName("QA-205: source=USER 이지만 type=GUILD 인 미션은 GUILD 로 분류되고 guild_name 을 노출한다")
        void guildMissionExposesGuildName() {
            Pageable pageable = PageRequest.of(0, 10);
            // 실제 데이터: 길드 미션은 type=GUILD 이지만 source 는 USER 로 저장된다.
            MissionParticipant participant = createParticipant(6L,
                createMission(15L, MissionType.GUILD, MissionSource.USER, "확신의루미길드", false),
                ParticipantStatus.IN_PROGRESS);
            when(participantRepository.searchUserMissionHistory(
                eq("user-1"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(participant)));
            when(executionRepository.sumExpEarnedByParticipantId(6L)).thenReturn(0);

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory("user-1", null, null, null, null, pageable);

            UserMissionHistoryAdminResponse row = result.content().get(0);
            assertThat(row.missionType()).isEqualTo("GUILD");
            assertThat(row.guildName()).isEqualTo("확신의루미길드");
        }

        @Test
        @DisplayName("type/source/날짜 필터가 Repository 로 그대로 전달된다")
        void forwardsFiltersToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2026, 6, 30, 0, 0);
            when(participantRepository.searchUserMissionHistory(
                eq("user-1"), eq(MissionType.PERSONAL), eq(MissionSource.SYSTEM),
                eq(start), eq(end), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory(
                    "user-1", MissionType.PERSONAL, MissionSource.SYSTEM, start, end, pageable);

            assertThat(result.content()).isEmpty();
        }

        @Test
        @DisplayName("빈 목록을 반환한다")
        void emptyHistory() {
            Pageable pageable = PageRequest.of(0, 10);
            when(participantRepository.searchUserMissionHistory(
                eq("user-1"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory("user-1", null, null, null, null, pageable);

            assertThat(result.content()).isEmpty();
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
