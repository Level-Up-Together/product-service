package io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record GuildMemberAdminResponse(
        Long id,
        Long guildId,
        String userId,
        String userNickname,
        String userProfileImage,
        String role,
        String status,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {
    public static GuildMemberAdminResponse from(
            GuildMember member, String nickname, String profileImage) {
        return new GuildMemberAdminResponse(
                member.getId(),
                member.getGuild().getId(),
                member.getUserId(),
                nickname,
                profileImage,
                member.getRole() != null ? member.getRole().name() : null,
                member.getStatus() != null ? member.getStatus().name() : null,
                member.getCreatedAt(),
                null,
                member.getCreatedAt(),
                member.getModifiedAt());
    }
}
