package io.pinkspider.leveluptogethermvp.adminservice.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record SeasonMvpGuildResponse(
    Long guildId,
    String name,
    String imageUrl,
    Integer level,
    Integer memberCount,
    Long seasonExp,
    Integer rank
) {
    public static SeasonMvpGuildResponse of(
        Long guildId,
        String name,
        String imageUrl,
        Integer level,
        Integer memberCount,
        Long seasonExp,
        Integer rank
    ) {
        return new SeasonMvpGuildResponse(
            guildId,
            name,
            imageUrl,
            level,
            memberCount,
            seasonExp,
            rank
        );
    }
}
