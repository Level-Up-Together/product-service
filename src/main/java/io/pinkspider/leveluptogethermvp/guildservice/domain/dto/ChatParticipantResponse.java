package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildChatParticipant;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatParticipantResponse {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("user_nickname")
    private String userNickname;

    @JsonProperty("joined_at")
    private LocalDateTime joinedAt;

    @JsonProperty("is_active")
    private Boolean isActive;

    public static ChatParticipantResponse from(GuildChatParticipant participant) {
        return ChatParticipantResponse.builder()
            .userId(participant.getUserId())
            .userNickname(participant.getUserNickname())
            .joinedAt(participant.getJoinedAt())
            .isActive(participant.getIsActive())
            .build();
    }
}
