package io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonNaming(SnakeCaseStrategy.class)
public record CreateSeasonRankRewardRequest(
    @NotNull @Min(1) Integer rankStart,
    @NotNull @Min(1) Integer rankEnd,
    @NotNull Long titleId,
    /* LUT-339: 보상 아이템 (shop_item ID, 선택) */
    Long itemId,
    Integer sortOrder
) {
    public CreateSeasonRankRewardRequest {
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }
}
