package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 길드 레벨업 이벤트
 */
public record GuildLevelUpEvent(
    String userId,
    Long guildId,
    String guildName,
    int newGuildLevel,
    LocalDateTime occurredAt
) implements DomainEvent {

    public GuildLevelUpEvent(String userId, Long guildId, String guildName, int newGuildLevel) {
        this(userId, guildId, guildName, newGuildLevel, LocalDateTime.now());
    }
}
