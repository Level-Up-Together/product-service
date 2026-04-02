package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ExecutionTimeUpdateRequest(
    @NotNull(message = "시작 시간은 필수입니다.")
    LocalDateTime startedAt,

    @NotNull(message = "종료 시간은 필수입니다.")
    LocalDateTime completedAt
) {}
