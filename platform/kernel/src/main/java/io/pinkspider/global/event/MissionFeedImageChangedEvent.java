package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 미션 수행 기록의 이미지가 변경되었을 때 발행
 * Feed에 공유된 이미지 URL을 동기화하기 위해 사용
 */
public record MissionFeedImageChangedEvent(
    String userId,
    Long executionId,
    String imageUrl,
    LocalDateTime occurredAt
) implements DomainEvent {

    public MissionFeedImageChangedEvent(String userId, Long executionId, String imageUrl) {
        this(userId, executionId, imageUrl, LocalDateTime.now());
    }
}
