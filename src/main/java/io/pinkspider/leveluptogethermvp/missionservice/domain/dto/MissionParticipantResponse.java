package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class MissionParticipantResponse {

    private Long id;
    private Long missionId;
    private String missionTitle;
    private String userId;
    private ParticipantStatus status;
    private Integer progress;
    private String note;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime joinedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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
