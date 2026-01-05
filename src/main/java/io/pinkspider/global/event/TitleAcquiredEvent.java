package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 칭호 획득 이벤트
 */
public record TitleAcquiredEvent(
    String userId,
    Long titleId,
    String titleName,
    String rarity,
    LocalDateTime occurredAt
) implements DomainEvent {

    public TitleAcquiredEvent(String userId, Long titleId, String titleName, String rarity) {
        this(userId, titleId, titleName, rarity, LocalDateTime.now());
    }
}
