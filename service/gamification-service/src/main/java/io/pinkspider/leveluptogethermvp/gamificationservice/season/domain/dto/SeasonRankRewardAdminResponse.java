package io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRankReward;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonRankRewardAdminResponse(
    Long id,
    Long seasonId,
    Integer rankStart,
    Integer rankEnd,
    String rankRangeDisplay,
    Long categoryId,
    String categoryName,
    String rankingTypeDisplay,
    Long titleId,
    String titleName,
    String titleRarity,
    String titlePositionType,
    Long itemId,
    String itemName,
    Integer sortOrder,
    Boolean isActive
) {

    public static SeasonRankRewardAdminResponse from(SeasonRankReward reward, Title title) {
        return new SeasonRankRewardAdminResponse(
            reward.getId(),
            reward.getSeason().getId(),
            reward.getRankStart(),
            reward.getRankEnd(),
            reward.getRankRangeDisplay(),
            reward.getCategoryId(),
            reward.getCategoryName(),
            reward.getRankingTypeDisplay(),
            reward.getTitleId(),
            reward.getTitleName(),
            title != null && title.getRarity() != null ? title.getRarity().name() : null,
            title != null && title.getPositionType() != null ? title.getPositionType().name() : null,
            reward.getItemId(),
            reward.getItemName(),
            reward.getSortOrder(),
            reward.getIsActive()
        );
    }

    public static SeasonRankRewardAdminResponse from(SeasonRankReward reward) {
        return from(reward, null);
    }
}
