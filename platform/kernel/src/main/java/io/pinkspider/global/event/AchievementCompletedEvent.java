package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 업적 달성 이벤트
 */
public record AchievementCompletedEvent(
    String userId,
    Long achievementId,
    String achievementName,
    LocalDateTime occurredAt
) implements DomainEvent {

    public AchievementCompletedEvent(String userId, Long achievementId, String achievementName) {
        this(userId, achievementId, achievementName, LocalDateTime.now());
    }
}
