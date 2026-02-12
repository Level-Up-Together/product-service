package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 피드 좋아요 이벤트
 */
public record FeedLikedEvent(
    String userId,           // 좋아요를 누른 사용자
    String feedOwnerId,      // 피드 작성자 (좋아요를 받은 사용자)
    Long feedId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public FeedLikedEvent(String userId, String feedOwnerId, Long feedId) {
        this(userId, feedOwnerId, feedId, LocalDateTime.now());
    }
}
