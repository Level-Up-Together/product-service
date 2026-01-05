package io.pinkspider.global.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 길드 미션 도착 이벤트
 */
public record GuildMissionArrivedEvent(
    String userId,           // 이벤트 발생시킨 사용자 (미션 생성자)
    List<String> memberIds,  // 알림 받을 길드 멤버들
    Long missionId,
    String missionTitle,
    LocalDateTime occurredAt
) implements DomainEvent {

    public GuildMissionArrivedEvent(String userId, List<String> memberIds, Long missionId, String missionTitle) {
        this(userId, memberIds, missionId, missionTitle, LocalDateTime.now());
    }
}
