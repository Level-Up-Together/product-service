package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 친구 요청 처리 완료 이벤트 (알림 삭제용)
 */
public record FriendRequestProcessedEvent(
    String userId,
    Long friendshipId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public FriendRequestProcessedEvent(String userId, Long friendshipId) {
        this(userId, friendshipId, LocalDateTime.now());
    }
}
