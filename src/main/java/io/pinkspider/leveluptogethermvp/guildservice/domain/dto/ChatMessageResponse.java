package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildChatMessage;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.ChatMessageType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    public static ChatMessageResponse from(GuildChatMessage message) {
        return ChatMessageResponse.builder()
            .id(message.getId())
            .guildId(message.getGuild().getId())
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
}
