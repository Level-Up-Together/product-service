package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuildResponse {

    private Long id;
    private String name;
    private String description;
    private GuildVisibility visibility;
    private String masterId;
    private Integer maxMembers;
    private Integer currentMemberCount;
    private String imageUrl;
    private Integer currentLevel;
    private Integer currentExp;
    private Integer totalExp;
    private LocalDateTime createdAt;

    public static GuildResponse from(Guild guild) {
        return GuildResponse.builder()
            .id(guild.getId())
            .name(guild.getName())
            .description(guild.getDescription())
            .visibility(guild.getVisibility())
            .masterId(guild.getMasterId())
            .maxMembers(guild.getMaxMembers())
            .imageUrl(guild.getImageUrl())
            .currentLevel(guild.getCurrentLevel())
            .currentExp(guild.getCurrentExp())
            .totalExp(guild.getTotalExp())
            .createdAt(guild.getCreatedAt())
            .build();
    }

    public static GuildResponse from(Guild guild, int memberCount) {
        return GuildResponse.builder()
            .id(guild.getId())
            .name(guild.getName())
            .description(guild.getDescription())
            .visibility(guild.getVisibility())
            .masterId(guild.getMasterId())
            .maxMembers(guild.getMaxMembers())
            .currentMemberCount(memberCount)
            .imageUrl(guild.getImageUrl())
            .currentLevel(guild.getCurrentLevel())
            .currentExp(guild.getCurrentExp())
            .totalExp(guild.getTotalExp())
            .createdAt(guild.getCreatedAt())
            .build();
    }
}
