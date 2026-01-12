package io.pinkspider.leveluptogethermvp.adminservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.SeasonRankReward;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonRankRewardResponse(
    Long id,
    Long seasonId,
    Integer rankStart,
    Integer rankEnd,
    String rankRangeDisplay,
    Long titleId,
    String titleName,
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
            reward.getTitleId(),
            reward.getTitleName(),
            reward.getSortOrder(),
            reward.getIsActive()
        );
    }
}
