package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.UserMissionHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.UserMissionHistoryAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.UserMissionEventRow;
import java.time.LocalDate;
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

    /** 네이티브 UNION 쿼리 프로젝션 테스트 스텁 */
    private record EventRow(
        Long getParticipantId,
        Long getMissionId,
        String getMissionTitle,
        String getMissionType,
        String getMissionSource,
        String getGuildName,
        String getStatus,
        Integer getExpEarned,
        LocalDateTime getEventAt
    ) implements UserMissionEventRow {}

    private static EventRow row(
        String missionType, String missionSource, String guildName, String status, Integer expEarned) {
        return new EventRow(1L, 10L, "테스트 미션", missionType, missionSource, guildName, status,
            expEarned, LocalDateTime.of(2026, 7, 1, 9, 30, 0));
    }

    @Nested
    @DisplayName("getUserMissionHistory 테스트")
    class GetUserMissionHistoryTest {

        @Test
        @DisplayName("IN_PROGRESS 수행 건은 STARTED 상태, exp_earned=null 로 매핑된다")
        void mapsInProgressToStarted() {
            Pageable pageable = PageRequest.of(0, 10);
            when(participantRepository.searchUserMissionEvents(
                eq("user-1"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row("PERSONAL", "USER", null, "IN_PROGRESS", 0))));

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory("user-1", null, null, null, null, pageable);

            assertThat(result.content()).hasSize(1);
            UserMissionHistoryAdminResponse mapped = result.content().get(0);
            assertThat(mapped.status()).isEqualTo("STARTED");
            assertThat(mapped.missionType()).isEqualTo("PERSONAL");
            assertThat(mapped.guildName()).isNull();
            assertThat(mapped.expEarned()).isNull();
            assertThat(mapped.eventAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 30, 0));
        }

        @Test
        @DisplayName("QA-205: source=SYSTEM 수행 건은 MISSION_BOOK 으로 분류되고 건별 EXP 를 노출한다")
        void mapsMissionBookExecution() {
            Pageable pageable = PageRequest.of(0, 10);
            when(participantRepository.searchUserMissionEvents(
                eq("user-1"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row("PERSONAL", "SYSTEM", null, "COMPLETED", 120))));

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory("user-1", null, null, null, null, pageable);

            UserMissionHistoryAdminResponse mapped = result.content().get(0);
            assertThat(mapped.missionType()).isEqualTo("MISSION_BOOK");
            assertThat(mapped.status()).isEqualTo("COMPLETED");
            assertThat(mapped.expEarned()).isEqualTo(120);
        }

        @Test
        @DisplayName("COMPLETED 수행 건의 exp_earned 가 null 이면 0 으로 보정한다")
        void completedWithNullExpFallsBackToZero() {
            Pageable pageable = PageRequest.of(0, 10);
            when(participantRepository.searchUserMissionEvents(
                eq("user-1"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row("PERSONAL", "USER", null, "COMPLETED", null))));

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory("user-1", null, null, null, null, pageable);

            assertThat(result.content().get(0).expEarned()).isEqualTo(0);
        }

        @Test
        @DisplayName("QA-205: source=USER 이지만 type=GUILD 인 수행 건은 GUILD 로 분류되고 guild_name 을 노출한다")
        void guildMissionExposesGuildName() {
            Pageable pageable = PageRequest.of(0, 10);
            // 실제 데이터: 길드 미션은 type=GUILD 이지만 source 는 USER 로 저장된다.
            when(participantRepository.searchUserMissionEvents(
                eq("user-1"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                    List.of(row("GUILD", "USER", "확신의루미길드", "COMPLETED", 30))));

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory("user-1", null, null, null, null, pageable);

            UserMissionHistoryAdminResponse mapped = result.content().get(0);
            assertThat(mapped.missionType()).isEqualTo("GUILD");
            assertThat(mapped.guildName()).isEqualTo("확신의루미길드");
        }

        @Test
        @DisplayName("GUILD 가 아닌 수행 건은 guild_name 을 노출하지 않는다")
        void nonGuildHidesGuildName() {
            Pageable pageable = PageRequest.of(0, 10);
            when(participantRepository.searchUserMissionEvents(
                eq("user-1"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                    List.of(row("PERSONAL", "USER", "잘못된길드명", "COMPLETED", 10))));

            UserMissionHistoryAdminPageResponse result =
                service.getUserMissionHistory("user-1", null, null, null, null, pageable);

            assertThat(result.content().get(0).guildName()).isNull();
        }

        @Test
        @DisplayName("type/source 는 enum name 문자열로, 날짜는 LocalDate 그대로 Repository 에 전달된다")
        void forwardsFiltersToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            LocalDate start = LocalDate.of(2026, 6, 1);
            LocalDate end = LocalDate.of(2026, 6, 30);
            when(participantRepository.searchUserMissionEvents(
                eq("user-1"), eq("PERSONAL"), eq("SYSTEM"), eq(start), eq(end), any(Pageable.class)))
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
            when(participantRepository.searchUserMissionEvents(
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
