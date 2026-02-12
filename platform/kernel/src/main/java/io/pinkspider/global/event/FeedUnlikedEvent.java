package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 피드 좋아요 취소 이벤트
 */
public record FeedUnlikedEvent(
    String userId,           // 좋아요를 취소한 사용자
    String feedOwnerId,      // 피드 작성자
    Long feedId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public FeedUnlikedEvent(String userId, String feedOwnerId, Long feedId) {
        this(userId, feedOwnerId, feedId, LocalDateTime.now());
    }
}
