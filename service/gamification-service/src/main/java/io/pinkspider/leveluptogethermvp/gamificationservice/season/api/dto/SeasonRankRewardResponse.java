package io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRankReward;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonRankRewardResponse(
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
    Integer sortOrder,
    Boolean isActive
) {
    public static SeasonRankRewardResponse from(SeasonRankReward reward) {
        return new SeasonRankRewardResponse(
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
            reward.getTitleRarity(),
            reward.getSortOrder(),
            reward.getIsActive()
        );
    }
}
