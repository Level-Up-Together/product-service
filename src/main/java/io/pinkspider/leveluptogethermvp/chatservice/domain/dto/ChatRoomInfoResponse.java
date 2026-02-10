package io.pinkspider.leveluptogethermvp.chatservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ChatRoomInfoResponse {

    private Long guildId;
    private String guildName;
    private String guildImageUrl;
    private Integer memberCount;
    private Integer participantCount;
    private Integer unreadMessageCount;
    private Long lastReadMessageId;

    public static ChatRoomInfoResponse of(Long guildId, String guildName, String guildImageUrl,
                                           int memberCount, int participantCount,
                                           int unreadMessageCount, Long lastReadMessageId) {
        return ChatRoomInfoResponse.builder()
            .guildId(guildId)
            .guildName(guildName)
            .guildImageUrl(guildImageUrl)
            .memberCount(memberCount)
            .participantCount(participantCount)
            .unreadMessageCount(unreadMessageCount)
            .lastReadMessageId(lastReadMessageId)
            .build();
    }
}
