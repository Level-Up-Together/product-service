package io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonNaming(SnakeCaseStrategy.class)
public record UpdateSeasonRankRewardRequest(
    @NotNull @Min(1) Integer rankStart,
    @NotNull @Min(1) Integer rankEnd,
    @NotNull Long titleId,
    Integer sortOrder
) {}
