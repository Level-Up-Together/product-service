package io.pinkspider.global.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 길드 채팅 메시지 이벤트
 * 채팅방에 새 메시지가 전송되었을 때 발생
 */
public record GuildChatMessageEvent(
    String userId,             // 메시지 발송자 (DomainEvent 인터페이스 구현)
    String senderNickname,     // 발송자 닉네임
    Long guildId,             // 길드 ID
    String guildName,         // 길드 이름
    Long messageId,           // 메시지 ID
    String messageContent,    // 메시지 내용 (미리보기용, 일부만)
    List<String> memberIds,   // 알림 받을 멤버들 (발송자 제외)
    LocalDateTime occurredAt
) implements DomainEvent {

    public GuildChatMessageEvent(String senderId, String senderNickname, Long guildId,
                                  String guildName, Long messageId, String messageContent,
                                  List<String> memberIds) {
        this(senderId, senderNickname, guildId, guildName, messageId, messageContent,
             memberIds, LocalDateTime.now());
    }

    /**
     * 미리보기용 메시지 (30자 제한)
     */
    public String getPreviewContent() {
        if (messageContent == null) return "";
        return messageContent.length() > 30
            ? messageContent.substring(0, 30) + "..."
            : messageContent;
    }
}
