package io.pinkspider.leveluptogethermvp.bffservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpGuildResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonMvpPlayerResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonRankRewardResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto.SeasonResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto.SeasonMyRankingResponse;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;

import java.util.List;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonDetailResponse(
    SeasonResponse season,
    List<SeasonRankRewardResponse> rankRewards,
    List<SeasonMvpPlayerResponse> playerRankings,
    List<SeasonMvpGuildResponse> guildRankings,
    SeasonMyRankingResponse myRanking,
    List<MissionCategoryResponse> categories
) {
    public static SeasonDetailResponse of(
        SeasonResponse season,
        List<SeasonRankRewardResponse> rankRewards,
        List<SeasonMvpPlayerResponse> playerRankings,
        List<SeasonMvpGuildResponse> guildRankings,
        SeasonMyRankingResponse myRanking,
        List<MissionCategoryResponse> categories
    ) {
        return new SeasonDetailResponse(
            season,
            rankRewards,
            playerRankings,
            guildRankings,
            myRanking,
            categories
        );
    }
}
