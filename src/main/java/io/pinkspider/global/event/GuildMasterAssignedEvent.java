package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 사용자가 길드 마스터가 되었을 때 발생하는 이벤트
 * - 길드 마스터 업적 체크용 (길드 창설 또는 마스터 위임 시)
 */
public record GuildMasterAssignedEvent(
    String userId,
    Long guildId,
    String guildName,
    LocalDateTime occurredAt
) implements DomainEvent {

    public GuildMasterAssignedEvent(String userId, Long guildId, String guildName) {
        this(userId, guildId, guildName, LocalDateTime.now());
    }
}
