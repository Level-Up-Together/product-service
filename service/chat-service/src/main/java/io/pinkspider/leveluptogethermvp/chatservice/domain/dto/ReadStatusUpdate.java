package io.pinkspider.leveluptogethermvp.chatservice.domain.dto;

// 읽음 상태 업데이트 브로드캐스트 페이로드 (/topic/guild/{guildId}/read)
public record ReadStatusUpdate(
    Long guildId,
    Long messageId,
    String userId,
    int unreadCount
) {}
