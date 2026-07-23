package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

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
import lombok.Setter;

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
    private String titleJa;
    private String description;
    private String descriptionEn;
    private String descriptionAr;
    private String descriptionJa;
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
    @Setter
    private String categoryName;
    @Setter
    private Boolean hasAchievedTarget;

    private LocalDateTime createdAt;

    private LocalDateTime modifiedAt;

    public static MissionTemplateResponse from(MissionTemplate template) {
        return from(template, null);
    }

    /**
     * LUT-255: 대표 필드(title/description)를 locale에 맞게 채운다 (locale null이면 한국어 기본값).
     * raw 다국어 필드(title_en 등)는 그대로 유지. categoryName localize는 meta 조회가 필요해
     * 서비스 레이어에서 덮어쓴다 (denormalized 한국어 이름은 fallback).
     */
    public static MissionTemplateResponse from(MissionTemplate template, String locale) {
        return MissionTemplateResponse.builder()
            .id(template.getId())
            .title(template.getLocalizedTitle(locale))
            .titleEn(template.getTitleEn())
            .titleAr(template.getTitleAr())
            .titleJa(template.getTitleJa())
            .description(template.getLocalizedDescription(locale))
            .descriptionEn(template.getDescriptionEn())
            .descriptionAr(template.getDescriptionAr())
            .descriptionJa(template.getDescriptionJa())
            .visibility(template.getVisibility())
            .source(template.getSource())
            .participationType(template.getParticipationType())
            .missionInterval(template.getMissionInterval())
            .durationMinutes(template.getDurationMinutes())
            .bonusExpOnFullCompletion(template.getBonusExpOnFullCompletion())
            .isPinned(template.getIsPinned())
            .targetDurationMinutes(template.getTargetDurationMinutes())
            .dailyExecutionLimit(template.getDailyExecutionLimit())
            .categoryId(template.getCategoryId())
            .categoryName(template.getCategoryName())
            .createdAt(template.getCreatedAt())
            .modifiedAt(template.getModifiedAt())
            .build();
    }
}
