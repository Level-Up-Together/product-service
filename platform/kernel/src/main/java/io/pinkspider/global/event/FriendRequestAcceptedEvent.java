package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 친구 요청 수락 이벤트
 */
public record FriendRequestAcceptedEvent(
    String userId,           // 요청을 수락한 사용자
    String requesterId,      // 원래 요청을 보낸 사용자 (알림 받을 사람)
    String accepterNickname,
    Long friendshipId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public FriendRequestAcceptedEvent(String userId, String requesterId, String accepterNickname, Long friendshipId) {
        this(userId, requesterId, accepterNickname, friendshipId, LocalDateTime.now());
    }
}
