package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record MissionAdminResponse(
    Long id,
    String title,
    String titleEn,
    String titleAr,
    String description,
    String descriptionEn,
    String descriptionAr,
    String status,
    String visibility,
    String type,
    String source,
    String participationType,
    Long baseMissionId,
    Boolean isCustomizable,
    String creatorId,
    String guildId,
    Integer maxParticipants,
    LocalDateTime startAt,
    LocalDateTime endAt,
    String missionInterval,
    Integer durationDays,
    Integer durationMinutes,
    Integer expPerCompletion,
    Integer bonusExpOnFullCompletion,
    Boolean isPinned,
    Integer targetDurationMinutes,
    Integer dailyExecutionLimit,
    Integer guildExpPerCompletion,
    Integer guildBonusExpOnFullCompletion,
    Long categoryId,
    String categoryName,
    String customCategory,
    LocalDateTime createdAt,
    LocalDateTime modifiedAt
) {

    public static MissionAdminResponse from(Mission entity) {
        return new MissionAdminResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getTitleEn(),
            entity.getTitleAr(),
            entity.getDescription(),
            entity.getDescriptionEn(),
            entity.getDescriptionAr(),
            entity.getStatus() != null ? entity.getStatus().name() : null,
            entity.getVisibility() != null ? entity.getVisibility().name() : null,
            entity.getType() != null ? entity.getType().name() : null,
            entity.getSource() != null ? entity.getSource().name() : null,
            entity.getParticipationType() != null ? entity.getParticipationType().name() : null,
            entity.getBaseMissionId(),
            entity.getIsCustomizable(),
            entity.getCreatorId(),
            entity.getGuildId(),
            entity.getMaxParticipants(),
            entity.getStartAt(),
            entity.getEndAt(),
            entity.getMissionInterval() != null ? entity.getMissionInterval().name() : null,
            entity.getDurationDays(),
            entity.getDurationMinutes(),
            entity.getExpPerCompletion(),
            entity.getBonusExpOnFullCompletion(),
            entity.getIsPinned(),
            entity.getTargetDurationMinutes(),
            entity.getDailyExecutionLimit(),
            entity.getGuildExpPerCompletion(),
            entity.getGuildBonusExpOnFullCompletion(),
            entity.getCategoryId(),
            entity.getCategoryName(),
            entity.getCustomCategory(),
            entity.getCreatedAt(),
            entity.getModifiedAt()
        );
    }
}
