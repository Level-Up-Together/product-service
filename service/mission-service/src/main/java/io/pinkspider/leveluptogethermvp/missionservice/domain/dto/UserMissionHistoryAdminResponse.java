package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record UserMissionHistoryAdminResponse(
    Long participantId,
    Long missionId,
    String missionTitle,
    String missionType,
    String guildName,
    String status,
    Integer expEarned,
    LocalDateTime eventAt
) {

    public static UserMissionHistoryAdminResponse from(MissionParticipant participant, Integer expEarnedRaw) {
        Mission mission = participant.getMission();
        String missionType = resolveMissionType(mission);
        String guildName = mission != null && mission.getSource() == MissionSource.GUILD ? mission.getGuildName() : null;
        String status = resolveStatus(mission, participant.getStatus());
        Integer expEarned = "COMPLETED".equals(status) ? (expEarnedRaw != null ? expEarnedRaw : 0) : null;

        return new UserMissionHistoryAdminResponse(
            participant.getId(),
            mission != null ? mission.getId() : null,
            mission != null ? mission.getTitle() : null,
            missionType,
            guildName,
            status,
            expEarned,
            participant.getJoinedAt()
        );
    }

    private static String resolveMissionType(Mission mission) {
        if (mission == null || mission.getSource() == null) {
            return null;
        }
        return switch (mission.getSource()) {
            case USER -> "PERSONAL";
            case SYSTEM -> "MISSION_BOOK";
            case GUILD -> "GUILD";
        };
    }

    private static String resolveStatus(Mission mission, ParticipantStatus status) {
        if (mission != null && Boolean.TRUE.equals(mission.getIsDeleted())) {
            return "DELETED";
        }
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING, ACCEPTED -> "CREATED";
            case IN_PROGRESS -> "STARTED";
            case COMPLETED, FAILED -> "COMPLETED";
            case WITHDRAWN -> "DELETED";
        };
    }
}
