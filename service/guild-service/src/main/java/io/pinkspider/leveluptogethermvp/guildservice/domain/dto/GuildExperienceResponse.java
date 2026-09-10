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
    // LUT-483: 레벨의 실제 기준값 (EXP 는 표기용)
    private Integer totalPoint;
    private Integer currentPoint;
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
            .totalPoint(guild.getTotalPoint())
            .currentPoint(guild.getCurrentPoint())
            .requiredExpForNextLevel(requiredExp)
            .maxMembers(guild.getMaxMembers())
            .levelTitle(levelTitle)
            .build();
    }
}
