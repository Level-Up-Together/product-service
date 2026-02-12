package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 친구 요청 이벤트
 */
public record FriendRequestEvent(
    String userId,           // 요청을 보낸 사용자
    String targetUserId,     // 요청을 받는 사용자
    String requesterNickname,
    Long friendshipId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public FriendRequestEvent(String userId, String targetUserId, String requesterNickname, Long friendshipId) {
        this(userId, targetUserId, requesterNickname, friendshipId, LocalDateTime.now());
    }
}
