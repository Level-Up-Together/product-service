package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MissionParticipantResponse {

    private Long id;
    private Long missionId;
    private String missionTitle;
    private String userId;
    private ParticipantStatus status;
    private Integer progress;
    private String note;

    private LocalDateTime joinedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    public static MissionParticipantResponse from(MissionParticipant participant) {
        return MissionParticipantResponse.builder()
            .id(participant.getId())
            .missionId(participant.getMission().getId())
            .missionTitle(participant.getMission().getTitle())
            .userId(participant.getUserId())
            .status(participant.getStatus())
            .progress(participant.getProgress())
            .note(participant.getNote())
            .joinedAt(participant.getJoinedAt())
            .completedAt(participant.getCompletedAt())
            .createdAt(participant.getCreatedAt())
            .build();
    }
}
