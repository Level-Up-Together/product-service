package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildInvitation;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildInvitationStatus;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 길드 초대 응답 DTO
 */
@Builder
public record GuildInvitationResponse(
    @JsonProperty("id")
    Long id,

    @JsonProperty("guild_id")
    Long guildId,

    @JsonProperty("guild_name")
    String guildName,

    @JsonProperty("guild_image_url")
    String guildImageUrl,

    @JsonProperty("inviter_id")
    String inviterId,

    @JsonProperty("inviter_nickname")
    String inviterNickname,

    @JsonProperty("invitee_id")
    String inviteeId,

    @JsonProperty("invitee_nickname")
    String inviteeNickname,

    @JsonProperty("message")
    String message,

    @JsonProperty("status")
    GuildInvitationStatus status,

    @JsonProperty("expires_at")
    LocalDateTime expiresAt,

    @JsonProperty("created_at")
    LocalDateTime createdAt
) {
    public static GuildInvitationResponse from(GuildInvitation invitation, String inviterNickname, String inviteeNickname) {
        return GuildInvitationResponse.builder()
            .id(invitation.getId())
            .guildId(invitation.getGuild().getId())
            .guildName(invitation.getGuild().getName())
            .guildImageUrl(invitation.getGuild().getImageUrl())
            .inviterId(invitation.getInviterId())
            .inviterNickname(inviterNickname)
            .inviteeId(invitation.getInviteeId())
            .inviteeNickname(inviteeNickname)
            .message(invitation.getMessage())
            .status(invitation.getStatus())
            .expiresAt(invitation.getExpiresAt())
            .createdAt(invitation.getCreatedAt())
            .build();
    }
}
