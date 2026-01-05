package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 친구 요청 거절 이벤트
 */
public record FriendRequestRejectedEvent(
    String userId,           // 요청을 거절한 사용자
    String requesterId,      // 원래 요청을 보낸 사용자 (알림 받을 사람)
    String rejecterNickname,
    Long friendshipId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public FriendRequestRejectedEvent(String userId, String requesterId, String rejecterNickname, Long friendshipId) {
        this(userId, requesterId, rejecterNickname, friendshipId, LocalDateTime.now());
    }
}
