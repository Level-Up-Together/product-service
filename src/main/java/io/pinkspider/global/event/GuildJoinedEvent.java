package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 사용자가 길드에 가입했을 때 발생하는 이벤트
 * - 길드 가입 업적 체크용
 */
public record GuildJoinedEvent(
    String userId,
    Long guildId,
    String guildName,
    LocalDateTime occurredAt
) implements DomainEvent {

    public GuildJoinedEvent(String userId, Long guildId, String guildName) {
        this(userId, guildId, guildName, LocalDateTime.now());
    }
}
