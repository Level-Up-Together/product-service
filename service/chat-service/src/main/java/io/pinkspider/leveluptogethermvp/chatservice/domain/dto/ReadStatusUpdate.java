package io.pinkspider.leveluptogethermvp.chatservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

// 읽음 상태 업데이트 브로드캐스트 페이로드 (/topic/guild/{guildId}/read)
// 프론트(chat-websocket.ts)는 message_id/unread_count(snake_case)로 매칭한다 —
// 어노테이션이 없으면 camelCase로 나가 수신측에서 조용히 무시된다 (LUT-395)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReadStatusUpdate(
    Long guildId,
    Long messageId,
    String userId,
    int unreadCount
) {}
