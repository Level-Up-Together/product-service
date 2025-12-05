package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
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
public class MissionResponse {

    private Long id;
    private String title;
    private String description;
    private MissionStatus status;
    private MissionVisibility visibility;
    private MissionType type;
    private String creatorId;
    private String guildId;
    private Integer maxParticipants;
    private Integer currentParticipants;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime modifiedAt;

    public static MissionResponse from(Mission mission) {
        return MissionResponse.builder()
            .id(mission.getId())
            .title(mission.getTitle())
            .description(mission.getDescription())
            .status(mission.getStatus())
            .visibility(mission.getVisibility())
            .type(mission.getType())
            .creatorId(mission.getCreatorId())
            .guildId(mission.getGuildId())
            .maxParticipants(mission.getMaxParticipants())
            .startDate(mission.getStartDate())
            .endDate(mission.getEndDate())
            .createdAt(mission.getCreatedAt())
            .modifiedAt(mission.getModifiedAt())
            .build();
    }

    public static MissionResponse from(Mission mission, int currentParticipants) {
        MissionResponse response = from(mission);
        response.setCurrentParticipants(currentParticipants);
        return response;
    }
}
