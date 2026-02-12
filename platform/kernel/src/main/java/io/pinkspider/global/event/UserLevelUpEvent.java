package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 사용자 레벨업 이벤트
 */
public record UserLevelUpEvent(
    String userId,
    int newLevel,
    long totalExp,
    LocalDateTime occurredAt
) implements DomainEvent {

    public UserLevelUpEvent(String userId, int newLevel, long totalExp) {
        this(userId, newLevel, totalExp, LocalDateTime.now());
    }
}
