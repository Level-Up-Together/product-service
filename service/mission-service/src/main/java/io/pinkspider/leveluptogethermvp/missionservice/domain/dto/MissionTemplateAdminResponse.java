package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionTemplate;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record MissionTemplateAdminResponse(
    Long id,
    String title,
    String titleEn,
    String titleAr,
    String description,
    String descriptionEn,
    String descriptionAr,
    String visibility,
    String source,
    String participationType,
    String missionInterval,
    Integer durationMinutes,
    Integer bonusExpOnFullCompletion,
    Boolean isPinned,
    Integer targetDurationMinutes,
    Integer dailyExecutionLimit,
    Long categoryId,
    String categoryName,
    String customCategory,
    String creatorId,
    LocalDateTime createdAt,
    LocalDateTime modifiedAt
) {

    public static MissionTemplateAdminResponse from(MissionTemplate entity) {
        return new MissionTemplateAdminResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getTitleEn(),
            entity.getTitleAr(),
            entity.getDescription(),
            entity.getDescriptionEn(),
            entity.getDescriptionAr(),
            entity.getVisibility() != null ? entity.getVisibility().name() : null,
            entity.getSource() != null ? entity.getSource().name() : null,
            entity.getParticipationType() != null ? entity.getParticipationType().name() : null,
            entity.getMissionInterval() != null ? entity.getMissionInterval().name() : null,
            entity.getDurationMinutes(),
            entity.getBonusExpOnFullCompletion(),
            entity.getIsPinned(),
            entity.getTargetDurationMinutes(),
            entity.getDailyExecutionLimit(),
            entity.getCategoryId(),
            entity.getCategoryName(),
            entity.getCustomCategory(),
            entity.getCreatorId(),
            entity.getCreatedAt(),
            entity.getModifiedAt()
        );
    }
}
