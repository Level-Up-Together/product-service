package io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record GuildAdminResponse(
    Long id,
    String name,
    String description,
    String visibility,
    String masterId,
    String masterNickname,
    Integer maxMembers,
    Integer currentMemberCount,
    String imageUrl,
    Boolean isActive,
    Boolean isBanned,
    LocalDateTime bannedAt,
    String bannedReason,
    Integer currentLevel,
    Integer currentExp,
    Integer totalExp,
    Long categoryId,
    String categoryName,
    String categoryIcon,
    String baseAddress,
    Double baseLatitude,
    Double baseLongitude,
    LocalDateTime createdAt,
    LocalDateTime modifiedAt
) {
    public static GuildAdminResponse from(Guild guild, int memberCount,
            String categoryName, String categoryIcon, String masterNickname) {
        return new GuildAdminResponse(
            guild.getId(),
            guild.getName(),
            guild.getDescription(),
            guild.getVisibility() != null ? guild.getVisibility().name() : null,
            guild.getMasterId(),
            masterNickname,
            guild.getMaxMembers(),
            memberCount,
            guild.getImageUrl(),
            guild.getIsActive(),
            guild.getIsBanned(),
            guild.getBannedAt(),
            guild.getBannedReason(),
            guild.getCurrentLevel(),
            guild.getCurrentExp(),
            guild.getTotalExp(),
            guild.getCategoryId(),
            categoryName,
            categoryIcon,
            guild.getBaseAddress(),
            guild.getBaseLatitude(),
            guild.getBaseLongitude(),
            guild.getCreatedAt(),
            guild.getModifiedAt()
        );
    }
}
