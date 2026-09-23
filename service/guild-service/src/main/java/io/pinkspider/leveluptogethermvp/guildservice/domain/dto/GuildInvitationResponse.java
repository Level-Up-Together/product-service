package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    LocalDateTime createdAt,

    // LUT-519: 받은 초대 카드용 길드 정보 (받은 초대 목록에서만 채움 — 그 외 응답에선 null 이라 생략)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("guild_current_member_count")
    Integer guildCurrentMemberCount,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("guild_current_level")
    Integer guildCurrentLevel
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

    /** LUT-519: 받은 초대 목록 — 길드 카드 렌더용 인원/레벨을 함께 채운다. */
    public static GuildInvitationResponse fromReceived(
        GuildInvitation invitation,
        String inviterNickname,
        String inviteeNickname,
        int guildCurrentMemberCount,
        int guildCurrentLevel) {
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
            .guildCurrentMemberCount(guildCurrentMemberCount)
            .guildCurrentLevel(guildCurrentLevel)
            .build();
    }
}
