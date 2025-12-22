package io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity.Friendship;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.enums.FriendshipStatus;
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
public class FriendResponse {
    private Long friendshipId;
    private String friendId;
    private String friendNickname;
    private String friendProfileImageUrl;
    private Integer friendLevel;
    private String friendTitle;
    private FriendshipStatus status;
    private LocalDateTime friendsSince;
    private Boolean isOnline;

    public static FriendResponse from(Friendship friendship, String currentUserId,
                                       String friendNickname, String profileImageUrl,
                                       Integer level, String title) {
        String friendId = friendship.getUserId().equals(currentUserId)
            ? friendship.getFriendId()
            : friendship.getUserId();

        return FriendResponse.builder()
            .friendshipId(friendship.getId())
            .friendId(friendId)
            .friendNickname(friendNickname)
            .friendProfileImageUrl(profileImageUrl)
            .friendLevel(level)
            .friendTitle(title)
            .status(friendship.getStatus())
            .friendsSince(friendship.getAcceptedAt())
            .build();
    }

    public static FriendResponse simpleFrom(Friendship friendship, String currentUserId) {
        String friendId = friendship.getUserId().equals(currentUserId)
            ? friendship.getFriendId()
            : friendship.getUserId();

        return FriendResponse.builder()
            .friendshipId(friendship.getId())
            .friendId(friendId)
            .status(friendship.getStatus())
            .friendsSince(friendship.getAcceptedAt())
            .build();
    }
}
