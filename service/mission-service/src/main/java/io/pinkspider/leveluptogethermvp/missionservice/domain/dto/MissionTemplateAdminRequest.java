package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonNaming(SnakeCaseStrategy.class)
public record MissionTemplateAdminRequest(
    @NotBlank(message = "미션 제목은 필수입니다.")
    @Size(max = 200)
    String title,

    @Size(max = 200)
    String titleEn,

    @Size(max = 200)
    String titleAr,

    String description,
    String descriptionEn,
    String descriptionAr,

    String visibility,
    String source,
    String participationType,
    String missionInterval,

    @Min(1)
    Integer durationMinutes,

    @Min(0)
    Integer bonusExpOnFullCompletion,

    Boolean isPinned,

    @Min(1)
    Integer targetDurationMinutes,

    @Min(1)
    Integer dailyExecutionLimit,

    Long categoryId,

    @Size(max = 50)
    String customCategory
) {}
