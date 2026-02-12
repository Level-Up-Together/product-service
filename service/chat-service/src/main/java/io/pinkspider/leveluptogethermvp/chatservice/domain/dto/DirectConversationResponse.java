package io.pinkspider.leveluptogethermvp.chatservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildDirectConversation;
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
public class DirectConversationResponse {

    private Long id;

    @JsonProperty("guild_id")
    private Long guildId;

    @JsonProperty("other_user_id")
    private String otherUserId;

    @JsonProperty("other_user_nickname")
    private String otherUserNickname;

    @JsonProperty("other_user_profile_image")
    private String otherUserProfileImage;

    @JsonProperty("last_message")
    private String lastMessage;

    @JsonProperty("last_message_at")
    private LocalDateTime lastMessageAt;

    @JsonProperty("unread_count")
    private Integer unreadCount;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public static DirectConversationResponse from(
            GuildDirectConversation conversation,
            String currentUserId,
            String otherUserNickname,
            String otherUserProfileImage,
            int unreadCount) {
        return DirectConversationResponse.builder()
            .id(conversation.getId())
            .guildId(conversation.getGuildId())
            .otherUserId(conversation.getOtherUserId(currentUserId))
            .otherUserNickname(otherUserNickname)
            .otherUserProfileImage(otherUserProfileImage)
            .lastMessage(conversation.getLastMessageContent())
            .lastMessageAt(conversation.getLastMessageAt())
            .unreadCount(unreadCount)
            .createdAt(conversation.getCreatedAt())
            .build();
    }
}
