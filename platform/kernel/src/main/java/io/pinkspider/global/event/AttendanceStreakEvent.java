package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 연속 출석 이벤트
 */
public record AttendanceStreakEvent(
    String userId,
    int streakDays,
    LocalDateTime occurredAt
) implements DomainEvent {

    public AttendanceStreakEvent(String userId, int streakDays) {
        this(userId, streakDays, LocalDateTime.now());
    }
}
