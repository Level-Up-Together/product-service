package io.pinkspider.leveluptogethermvp.bffservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonMyRankingResponse(
    Integer playerRank,
    Long playerSeasonExp,
    Integer guildRank,
    Long guildSeasonExp,
    Long guildId,
    String guildName
) {
    public static SeasonMyRankingResponse of(
        Integer playerRank,
        Long playerSeasonExp,
        Integer guildRank,
        Long guildSeasonExp,
        Long guildId,
        String guildName
    ) {
        return new SeasonMyRankingResponse(
            playerRank,
            playerSeasonExp,
            guildRank,
            guildSeasonExp,
            guildId,
            guildName
        );
    }

    /**
     * 랭킹 정보가 없는 경우 (시즌에 활동이 없는 경우)
     */
    public static SeasonMyRankingResponse empty() {
        return new SeasonMyRankingResponse(null, 0L, null, null, null, null);
    }
}
