package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 길드 창설 이벤트
 */
public record GuildCreatedEvent(
    String userId,
    Long guildId,
    String guildName,
    LocalDateTime occurredAt
) implements DomainEvent {

    public GuildCreatedEvent(String userId, Long guildId, String guildName) {
        this(userId, guildId, guildName, LocalDateTime.now());
    }
}
