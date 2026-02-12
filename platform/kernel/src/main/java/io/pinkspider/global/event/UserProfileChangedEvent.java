package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 사용자 프로필(닉네임, 프로필 사진, 레벨) 변경 시 발행
 * 다른 서비스의 스냅샷 데이터를 동기화하기 위해 사용
 */
public record UserProfileChangedEvent(
    String userId,
    String nickname,
    String profileImageUrl,
    Integer level,
    LocalDateTime occurredAt
) implements DomainEvent {

    public UserProfileChangedEvent(String userId, String nickname, String profileImageUrl, Integer level) {
        this(userId, nickname, profileImageUrl, level, LocalDateTime.now());
    }
}
