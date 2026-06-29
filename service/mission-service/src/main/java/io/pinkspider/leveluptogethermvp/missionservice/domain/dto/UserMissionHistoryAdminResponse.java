package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
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
        String guildName =
            mission != null && mission.getType() == MissionType.GUILD ? mission.getGuildName() : null;
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
        if (mission == null) {
            return null;
        }
        // QA-205: 길드 여부는 Mission.type(GUILD)으로 판별한다.
        // 길드 미션도 source 는 USER 로 저장되므로 source 만으로는 구분할 수 없다.
        // 미션북은 시스템 템플릿 출처(source=SYSTEM)로 식별한다.
        if (mission.getType() == MissionType.GUILD) {
            return "GUILD";
        }
        if (mission.getSource() == MissionSource.SYSTEM) {
            return "MISSION_BOOK";
        }
        return "PERSONAL";
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
