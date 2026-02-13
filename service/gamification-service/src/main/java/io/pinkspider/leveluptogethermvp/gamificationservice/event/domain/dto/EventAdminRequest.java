package io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record EventAdminRequest(
    @NotBlank(message = "이벤트명은 필수입니다")
    @Size(max = 100, message = "이벤트명은 100자를 초과할 수 없습니다")
    String name,

    @Size(max = 100)
    String nameEn,

    @Size(max = 100)
    String nameAr,

    String description,

    String descriptionEn,

    String descriptionAr,

    @Size(max = 500)
    String imageUrl,

    @NotNull(message = "시작 일시는 필수입니다")
    LocalDateTime startAt,

    @NotNull(message = "종료 일시는 필수입니다")
    LocalDateTime endAt,

    Long rewardTitleId,

    Boolean isActive
) {}
