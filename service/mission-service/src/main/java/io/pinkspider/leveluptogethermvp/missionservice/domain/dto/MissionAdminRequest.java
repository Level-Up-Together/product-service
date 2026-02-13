package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record MissionAdminRequest(
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

    @NotNull(message = "미션 상태는 필수입니다.")
    String status,

    @NotNull(message = "공개 여부는 필수입니다.")
    String visibility,

    @NotNull(message = "미션 타입은 필수입니다.")
    String type,

    String source,
    String participationType,
    Boolean isCustomizable,

    @NotBlank(message = "생성자 ID는 필수입니다.")
    String creatorId,

    String guildId,

    @Min(1)
    Integer maxParticipants,

    LocalDateTime startAt,
    LocalDateTime endAt,
    String missionInterval,

    @Min(1)
    Integer durationDays,

    @Min(1)
    Integer durationMinutes,

    @Min(0)
    Integer expPerCompletion,

    @Min(0)
    Integer bonusExpOnFullCompletion,

    Boolean isPinned,

    @Min(1)
    Integer targetDurationMinutes,

    @Min(1)
    Integer dailyExecutionLimit,

    @Min(0)
    Integer guildExpPerCompletion,

    @Min(0)
    Integer guildBonusExpOnFullCompletion
) {}
