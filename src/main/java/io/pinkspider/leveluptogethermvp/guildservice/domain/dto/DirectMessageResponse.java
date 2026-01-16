package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildDirectMessage;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessageResponse {

    private Long id;

    @JsonProperty("conversation_id")
    private Long conversationId;

    @JsonProperty("sender_id")
    private String senderId;

    @JsonProperty("sender_nickname")
    private String senderNickname;

    private String content;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("is_read")
    private Boolean isRead;

    @JsonProperty("read_at")
    private LocalDateTime readAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public static DirectMessageResponse from(GuildDirectMessage message) {
        return DirectMessageResponse.builder()
            .id(message.getId())
            .conversationId(message.getConversation().getId())
            .senderId(message.getSenderId())
            .senderNickname(message.getSenderNickname())
            .content(message.getContent())
            .imageUrl(message.getImageUrl())
            .isRead(message.getIsRead())
            .readAt(message.getReadAt())
            .createdAt(message.getCreatedAt())
            .build();
    }
}
