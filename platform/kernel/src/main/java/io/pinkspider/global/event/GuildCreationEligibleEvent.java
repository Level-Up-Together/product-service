package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 길드 창설 가능 레벨(20) 도달 이벤트
 */
public record GuildCreationEligibleEvent(
    String userId,
    int level,
    LocalDateTime occurredAt
) implements DomainEvent {

    public GuildCreationEligibleEvent(String userId, int level) {
        this(userId, level, LocalDateTime.now());
    }
}
