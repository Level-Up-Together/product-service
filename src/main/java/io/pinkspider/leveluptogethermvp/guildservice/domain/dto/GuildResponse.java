package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildJoinType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuildResponse {

    private Long id;
    private String name;
    private String description;
    private GuildVisibility visibility;
    private GuildJoinType joinType;
    private String masterId;
    private Integer maxMembers;
    private Integer currentMemberCount;
    private String imageUrl;
    private Integer currentLevel;
    private Integer currentExp;
    private Integer totalExp;
    private Long categoryId;
    private String categoryName;
    private String categoryIcon;
    private String baseAddress;
    private Double baseLatitude;
    private Double baseLongitude;
    private LocalDateTime createdAt;

    // 신고 처리중 여부
    private Boolean isUnderReview;

    public static GuildResponse from(Guild guild) {
        return GuildResponse.builder()
            .id(guild.getId())
            .name(guild.getName())
            .description(guild.getDescription())
            .visibility(guild.getVisibility())
            .joinType(guild.getJoinType())
            .masterId(guild.getMasterId())
            .maxMembers(guild.getMaxMembers())
            .imageUrl(guild.getImageUrl())
            .currentLevel(guild.getCurrentLevel())
            .currentExp(guild.getCurrentExp())
            .totalExp(guild.getTotalExp())
            .categoryId(guild.getCategoryId())
            .baseAddress(guild.getBaseAddress())
            .baseLatitude(guild.getBaseLatitude())
            .baseLongitude(guild.getBaseLongitude())
            .createdAt(guild.getCreatedAt())
            .build();
    }

    public static GuildResponse from(Guild guild, int memberCount) {
        return GuildResponse.builder()
            .id(guild.getId())
            .name(guild.getName())
            .description(guild.getDescription())
            .visibility(guild.getVisibility())
            .joinType(guild.getJoinType())
            .masterId(guild.getMasterId())
            .maxMembers(guild.getMaxMembers())
            .currentMemberCount(memberCount)
            .imageUrl(guild.getImageUrl())
            .currentLevel(guild.getCurrentLevel())
            .currentExp(guild.getCurrentExp())
            .totalExp(guild.getTotalExp())
            .categoryId(guild.getCategoryId())
            .baseAddress(guild.getBaseAddress())
            .baseLatitude(guild.getBaseLatitude())
            .baseLongitude(guild.getBaseLongitude())
            .createdAt(guild.getCreatedAt())
            .build();
    }

    public static GuildResponse from(Guild guild, int memberCount, String categoryName, String categoryIcon) {
        return GuildResponse.builder()
            .id(guild.getId())
            .name(guild.getName())
            .description(guild.getDescription())
            .visibility(guild.getVisibility())
            .joinType(guild.getJoinType())
            .masterId(guild.getMasterId())
            .maxMembers(guild.getMaxMembers())
            .currentMemberCount(memberCount)
            .imageUrl(guild.getImageUrl())
            .currentLevel(guild.getCurrentLevel())
            .currentExp(guild.getCurrentExp())
            .totalExp(guild.getTotalExp())
            .categoryId(guild.getCategoryId())
            .categoryName(categoryName)
            .categoryIcon(categoryIcon)
            .baseAddress(guild.getBaseAddress())
            .baseLatitude(guild.getBaseLatitude())
            .baseLongitude(guild.getBaseLongitude())
            .createdAt(guild.getCreatedAt())
            .build();
    }
}
