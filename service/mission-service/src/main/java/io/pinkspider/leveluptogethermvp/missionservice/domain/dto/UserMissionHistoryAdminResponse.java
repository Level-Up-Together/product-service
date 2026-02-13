package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record UserMissionHistoryAdminResponse(
    Long participantId,
    Long missionId,
    String missionTitle,
    String missionType,
    String status,
    Integer progress,
    LocalDateTime joinedAt,
    LocalDateTime completedAt,
    Long totalExecutions,
    Long completedExecutions,
    Integer totalExpEarned
) {

    public static UserMissionHistoryAdminResponse from(
            MissionParticipant participant,
            long totalExecutions,
            long completedExecutions,
            int totalExpEarned) {
        return new UserMissionHistoryAdminResponse(
            participant.getId(),
            participant.getMission() != null ? participant.getMission().getId() : null,
            participant.getMission() != null ? participant.getMission().getTitle() : null,
            participant.getMission() != null && participant.getMission().getType() != null
                ? participant.getMission().getType().name() : null,
            participant.getStatus() != null ? participant.getStatus().name() : null,
            participant.getProgress(),
            participant.getJoinedAt(),
            participant.getCompletedAt(),
            totalExecutions,
            completedExecutions,
            totalExpEarned
        );
    }
}
