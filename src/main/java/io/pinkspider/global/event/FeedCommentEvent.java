package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 피드 댓글 작성 이벤트
 */
public record FeedCommentEvent(
    String userId,           // 댓글 작성자
    String feedOwnerId,      // 피드 주인 (알림 받을 사람)
    String commenterNickname,
    Long feedId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public FeedCommentEvent(String userId, String feedOwnerId, String commenterNickname, Long feedId) {
        this(userId, feedOwnerId, commenterNickname, feedId, LocalDateTime.now());
    }
}
