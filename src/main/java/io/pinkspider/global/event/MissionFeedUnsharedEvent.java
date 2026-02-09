package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 미션 피드 공유가 취소되었을 때 발행
 * Feed 레코드를 삭제하기 위해 사용
 */
public record MissionFeedUnsharedEvent(
    String userId,
    Long executionId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public MissionFeedUnsharedEvent(String userId, Long executionId) {
        this(userId, executionId, LocalDateTime.now());
    }
}
