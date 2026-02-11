package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 친구 삭제 이벤트
 */
public record FriendRemovedEvent(
    String userId,           // 친구를 삭제한 사용자
    String removedFriendId,  // 삭제된 친구
    LocalDateTime occurredAt
) implements DomainEvent {

    public FriendRemovedEvent(String userId, String removedFriendId) {
        this(userId, removedFriendId, LocalDateTime.now());
    }
}
