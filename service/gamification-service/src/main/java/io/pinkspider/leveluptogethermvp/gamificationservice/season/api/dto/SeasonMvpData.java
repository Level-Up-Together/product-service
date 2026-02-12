package io.pinkspider.leveluptogethermvp.gamificationservice.season.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonMvpData(
    SeasonResponse currentSeason,
    List<SeasonMvpPlayerResponse> seasonMvpPlayers,
    List<SeasonMvpGuildResponse> seasonMvpGuilds
) {
    public static SeasonMvpData of(
        SeasonResponse currentSeason,
        List<SeasonMvpPlayerResponse> seasonMvpPlayers,
        List<SeasonMvpGuildResponse> seasonMvpGuilds
    ) {
        return new SeasonMvpData(currentSeason, seasonMvpPlayers, seasonMvpGuilds);
    }
}
