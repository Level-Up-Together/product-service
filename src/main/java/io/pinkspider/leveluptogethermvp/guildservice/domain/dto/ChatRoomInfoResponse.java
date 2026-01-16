package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
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

    public static ChatRoomInfoResponse of(Guild guild, int memberCount, int participantCount, int unreadMessageCount, Long lastReadMessageId) {
        return ChatRoomInfoResponse.builder()
            .guildId(guild.getId())
            .guildName(guild.getName())
            .guildImageUrl(guild.getImageUrl())
            .memberCount(memberCount)
            .participantCount(participantCount)
            .unreadMessageCount(unreadMessageCount)
            .lastReadMessageId(lastReadMessageId)
            .build();
    }
}
