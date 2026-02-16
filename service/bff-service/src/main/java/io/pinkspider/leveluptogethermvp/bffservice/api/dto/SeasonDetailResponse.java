package io.pinkspider.leveluptogethermvp.bffservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.facade.dto.SeasonDto;
import io.pinkspider.global.facade.dto.SeasonMvpGuildDto;
import io.pinkspider.global.facade.dto.SeasonMvpPlayerDto;
import io.pinkspider.global.facade.dto.SeasonMyRankingDto;
import io.pinkspider.global.facade.dto.SeasonRankRewardDto;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;

import java.util.List;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonDetailResponse(
    SeasonDto season,
    List<SeasonRankRewardDto> rankRewards,
    List<SeasonMvpPlayerDto> playerRankings,
    List<SeasonMvpGuildDto> guildRankings,
    SeasonMyRankingDto myRanking,
    List<MissionCategoryResponse> categories
) {
    public static SeasonDetailResponse of(
        SeasonDto season,
        List<SeasonRankRewardDto> rankRewards,
        List<SeasonMvpPlayerDto> playerRankings,
        List<SeasonMvpGuildDto> guildRankings,
        SeasonMyRankingDto myRanking,
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
