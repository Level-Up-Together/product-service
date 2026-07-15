package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.GuildMissionHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.GuildMissionHistoryAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.UserMissionHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.UserMissionHistoryAdminResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.GuildMissionEventRow;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.UserMissionEventRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "missionTransactionManager")
public class MissionParticipantAdminService {

    private final MissionParticipantRepository participantRepository;
    private final MissionExecutionRepository executionRepository;
    private final UserQueryFacade userQueryFacade;

    /**
     * QA-205: 유저 미션 수행 기록 조회 — 한 행 = 한 수행 건 (일반 미션 + 고정 미션).
     */
    public UserMissionHistoryAdminPageResponse getUserMissionHistory(
        String userId,
        MissionType type,
        MissionSource source,
        LocalDate startDate,
        LocalDate endDate,
        Pageable pageable) {

        Page<UserMissionEventRow> eventPage =
            participantRepository.searchUserMissionEvents(
                userId,
                type != null ? type.name() : null,
                source != null ? source.name() : null,
                startDate, endDate, pageable);

        List<UserMissionHistoryAdminResponse> content = eventPage.getContent().stream()
            .map(UserMissionHistoryAdminResponse::fromEvent)
            .toList();

        return UserMissionHistoryAdminPageResponse.from(eventPage, content);
    }

    /**
     * LUT-239: 길드 미션 수행 기록 조회 — 한 행 = 한 수행 건 (일반 미션 + 고정 미션).
     * 수행자 닉네임은 UserQueryFacade 로 배치 조회하여 채운다.
     */
    public GuildMissionHistoryAdminPageResponse getGuildMissionHistory(
        Long guildId, LocalDate startDate, LocalDate endDate, Pageable pageable) {

        Page<GuildMissionEventRow> eventPage =
            participantRepository.searchGuildMissionEvents(
                String.valueOf(guildId), startDate, endDate, pageable);

        List<String> userIds = eventPage.getContent().stream()
            .map(GuildMissionEventRow::getUserId)
            .distinct()
            .toList();
        Map<String, UserProfileInfo> profiles =
            userIds.isEmpty() ? Map.of() : userQueryFacade.getUserProfiles(userIds);

        List<GuildMissionHistoryAdminResponse> content = eventPage.getContent().stream()
            .map(row -> {
                UserProfileInfo profile = profiles.get(row.getUserId());
                return GuildMissionHistoryAdminResponse.fromEvent(
                    row, profile != null ? profile.nickname() : null);
            })
            .toList();

        return GuildMissionHistoryAdminPageResponse.from(eventPage, content);
    }

    public long countParticipantsByUserId(String userId) {
        return participantRepository.countByUserId(userId);
    }

    public long countExecutionsByUserId(String userId) {
        return executionRepository.countByUserId(userId);
    }

    public long countCompletedExecutionsByUserId(String userId) {
        return executionRepository.countCompletedByUserId(userId);
    }
}
