package io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonAdminRequest(
        @NotBlank(message = "시즌 타이틀은 필수입니다") @Size(max = 100, message = "시즌 타이틀은 100자를 초과할 수 없습니다")
                String title,
        @Size(max = 500, message = "시즌 설명은 500자를 초과할 수 없습니다") String description,
        @NotNull(message = "시작 일시는 필수입니다") LocalDateTime startAt,
        @NotNull(message = "종료 일시는 필수입니다") LocalDateTime endAt,
        Boolean isActive,
        Long rewardTitleId,
        String rewardTitleName,
        Integer sortOrder,
        String createdBy,
        String modifiedBy) {}
