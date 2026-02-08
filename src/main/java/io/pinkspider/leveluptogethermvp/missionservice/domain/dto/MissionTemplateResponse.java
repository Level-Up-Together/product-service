package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionTemplate;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionParticipationType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MissionTemplateResponse {

    private Long id;
    private String title;
    private String titleEn;
    private String titleAr;
    private String description;
    private String descriptionEn;
    private String descriptionAr;
    private MissionVisibility visibility;
    private MissionSource source;
    private MissionParticipationType participationType;
    private MissionInterval missionInterval;
    private Integer durationMinutes;
    private Integer bonusExpOnFullCompletion;
    private Boolean isPinned;
    private Integer targetDurationMinutes;
    private Integer dailyExecutionLimit;
    private Long categoryId;
    private String categoryName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime modifiedAt;

    public static MissionTemplateResponse from(MissionTemplate template) {
        return MissionTemplateResponse.builder()
            .id(template.getId())
            .title(template.getTitle())
            .titleEn(template.getTitleEn())
            .titleAr(template.getTitleAr())
            .description(template.getDescription())
            .descriptionEn(template.getDescriptionEn())
            .descriptionAr(template.getDescriptionAr())
            .visibility(template.getVisibility())
            .source(template.getSource())
            .participationType(template.getParticipationType())
            .missionInterval(template.getMissionInterval())
            .durationMinutes(template.getDurationMinutes())
            .bonusExpOnFullCompletion(template.getBonusExpOnFullCompletion())
            .isPinned(template.getIsPinned())
            .targetDurationMinutes(template.getTargetDurationMinutes())
            .dailyExecutionLimit(template.getDailyExecutionLimit())
            .categoryId(template.getCategory() != null ? template.getCategory().getId() : null)
            .categoryName(template.getCategoryName())
            .createdAt(template.getCreatedAt())
            .modifiedAt(template.getModifiedAt())
            .build();
    }
}
