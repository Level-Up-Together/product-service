package io.pinkspider.leveluptogethermvp.chatservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildChatMessage;
import io.pinkspider.leveluptogethermvp.chatservice.domain.enums.ChatMessageType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ChatMessageResponse {
    private Long id;
    private Long guildId;
    private String senderId;
    private String senderNickname;
    private ChatMessageType messageType;
    private String content;
    private String imageUrl;
    private String referenceType;
    private Long referenceId;
    private Boolean isSystemMessage;
    private LocalDateTime createdAt;
    private Integer unreadCount;

    public static ChatMessageResponse from(GuildChatMessage message) {
        return ChatMessageResponse.builder()
            .id(message.getId())
            .guildId(message.getGuildId())
            .senderId(message.getSenderId())
            .senderNickname(message.getSenderNickname())
            .messageType(message.getMessageType())
            .content(message.getContent())
            .imageUrl(message.getImageUrl())
            .referenceType(message.getReferenceType())
            .referenceId(message.getReferenceId())
            .isSystemMessage(message.isSystemMessage())
            .createdAt(message.getCreatedAt())
            .build();
    }

    public static ChatMessageResponse from(GuildChatMessage message, int unreadCount) {
        return ChatMessageResponse.builder()
            .id(message.getId())
            .guildId(message.getGuildId())
            .senderId(message.getSenderId())
            .senderNickname(message.getSenderNickname())
            .messageType(message.getMessageType())
            .content(message.getContent())
            .imageUrl(message.getImageUrl())
            .referenceType(message.getReferenceType())
            .referenceId(message.getReferenceId())
            .isSystemMessage(message.isSystemMessage())
            .createdAt(message.getCreatedAt())
            .unreadCount(unreadCount)
            .build();
    }
}
