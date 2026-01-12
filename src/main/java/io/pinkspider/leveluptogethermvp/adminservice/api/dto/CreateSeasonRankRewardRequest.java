package io.pinkspider.leveluptogethermvp.adminservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonNaming(SnakeCaseStrategy.class)
public record CreateSeasonRankRewardRequest(
    @NotNull @Min(1) Integer rankStart,
    @NotNull @Min(1) Integer rankEnd,
    @NotNull Long titleId,
    Integer sortOrder
) {
    public CreateSeasonRankRewardRequest {
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }
}
