package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionParticipationType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
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
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MissionResponse {

    private Long id;
    private String title;
    private String titleEn;
    private String titleAr;
    private String description;
    private String descriptionEn;
    private String descriptionAr;
    private MissionStatus status;
    private MissionVisibility visibility;
    private MissionType type;
    private MissionSource source;
    private MissionParticipationType participationType;
    private Boolean isCustomizable;
    private Boolean isPinned;
    private String creatorId;
    private String guildId;
    private String guildName;
    private Integer maxParticipants;
    private Integer currentParticipants;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    private MissionInterval missionInterval;
    private Integer durationDays;
    private Integer durationMinutes;
    private Integer expPerCompletion;
    private Integer bonusExpOnFullCompletion;

    // 카테고리 정보
    private Long categoryId;
    private String categoryName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime modifiedAt;

    // 신고 처리중 여부
    private Boolean isUnderReview;

    public static MissionResponse from(Mission mission) {
        return MissionResponse.builder()
            .id(mission.getId())
            .title(mission.getTitle())
            .titleEn(mission.getTitleEn())
            .titleAr(mission.getTitleAr())
            .description(mission.getDescription())
            .descriptionEn(mission.getDescriptionEn())
            .descriptionAr(mission.getDescriptionAr())
            .status(mission.getStatus())
            .visibility(mission.getVisibility())
            .type(mission.getType())
            .source(mission.getSource())
            .participationType(mission.getParticipationType())
            .isCustomizable(mission.getIsCustomizable())
            .isPinned(mission.getIsPinned())
            .creatorId(mission.getCreatorId())
            .guildId(mission.getGuildId())
            .guildName(mission.getGuildName())
            .maxParticipants(mission.getMaxParticipants())
            .startAt(mission.getStartAt())
            .endAt(mission.getEndAt())
            .missionInterval(mission.getMissionInterval())
            .durationDays(mission.getDurationDays())
            .durationMinutes(mission.getDurationMinutes())
            .expPerCompletion(mission.getExpPerCompletion())
            .bonusExpOnFullCompletion(mission.getBonusExpOnFullCompletion())
            .categoryId(mission.getCategory() != null ? mission.getCategory().getId() : null)
            .categoryName(mission.getCategoryName())
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
