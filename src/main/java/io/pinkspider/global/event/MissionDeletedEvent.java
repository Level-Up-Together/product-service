package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 미션이 삭제되었을 때 발행
 * 관련 Feed 레코드를 cascade 삭제하기 위해 사용
 */
public record MissionDeletedEvent(
    String userId,
    Long missionId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public MissionDeletedEvent(String userId, Long missionId) {
        this(userId, missionId, LocalDateTime.now());
    }
}
