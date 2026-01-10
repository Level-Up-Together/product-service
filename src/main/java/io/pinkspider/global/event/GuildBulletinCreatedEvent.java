package io.pinkspider.global.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 길드 공지사항 등록 이벤트
 */
public record GuildBulletinCreatedEvent(
    String userId,           // 이벤트 발생시킨 사용자 (공지글 작성자)
    List<String> memberIds,  // 알림 받을 길드 멤버들
    Long guildId,
    String guildName,
    Long postId,
    String postTitle,
    LocalDateTime occurredAt
) implements DomainEvent {

    public GuildBulletinCreatedEvent(String userId, List<String> memberIds, Long guildId,
                                     String guildName, Long postId, String postTitle) {
        this(userId, memberIds, guildId, guildName, postId, postTitle, LocalDateTime.now());
    }
}
