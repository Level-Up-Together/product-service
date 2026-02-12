package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 길드 초대 이벤트
 * 비공개 길드 마스터가 다른 유저를 초대할 때 발생
 */
public record GuildInvitationEvent(
    String userId,           // 초대한 유저 ID (마스터/부마스터)
    String inviteeId,        // 초대 받는 유저 ID
    String inviterNickname,  // 초대한 유저 닉네임
    Long guildId,
    String guildName,
    Long invitationId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public GuildInvitationEvent(
        String userId,
        String inviteeId,
        String inviterNickname,
        Long guildId,
        String guildName,
        Long invitationId
    ) {
        this(userId, inviteeId, inviterNickname, guildId, guildName, invitationId, LocalDateTime.now());
    }
}
