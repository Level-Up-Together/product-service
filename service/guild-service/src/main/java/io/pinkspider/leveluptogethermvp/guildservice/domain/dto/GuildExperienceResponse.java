package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuildExperienceResponse {

    private Long guildId;
    private String guildName;
    private Integer currentLevel;
    private Integer currentExp;
    private Integer totalExp;
    private Integer requiredExpForNextLevel;
    private Integer maxMembers;
    private String levelTitle;

    public static GuildExperienceResponse from(Guild guild, Integer requiredExp, String levelTitle) {
        return GuildExperienceResponse.builder()
            .guildId(guild.getId())
            .guildName(guild.getName())
            .currentLevel(guild.getCurrentLevel())
            .currentExp(guild.getCurrentExp())
            .totalExp(guild.getTotalExp())
            .requiredExpForNextLevel(requiredExp)
            .maxMembers(guild.getMaxMembers())
            .levelTitle(levelTitle)
            .build();
    }
}
