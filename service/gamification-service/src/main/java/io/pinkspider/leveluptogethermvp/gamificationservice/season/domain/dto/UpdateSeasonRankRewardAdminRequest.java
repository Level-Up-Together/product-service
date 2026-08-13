package io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonNaming(SnakeCaseStrategy.class)
public record UpdateSeasonRankRewardAdminRequest(
    @NotNull @Min(1) Integer rankStart,
    @NotNull @Min(1) Integer rankEnd,
    Long categoryId,
    String categoryName,
    @NotNull Long titleId,
    @NotBlank String titleName,
    @NotNull TitleRarity titleRarity,
    @NotNull TitlePosition titlePositionType,
    /* LUT-339: 보상 아이템 (shop_item ID, 선택) — 칭호와 동시 지급 */
    Long itemId,
    Integer sortOrder
) {}
