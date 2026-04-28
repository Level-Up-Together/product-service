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
    /** 좌측 칭호 (형용사형). 기존 호환을 위해 friendTitle과 동일값 유지. */
    private String friendTitle;
    /** 좌측 칭호 (QA-93: 명시적으로 분리). */
    private String friendTitleLeft;
    /** 우측 칭호 (QA-93). */
    private String friendTitleRight;
    private FriendshipStatus status;
    private LocalDateTime friendsSince;
    private Boolean isOnline;

    public static FriendResponse from(Friendship friendship, String currentUserId,
                                       String friendNickname, String profileImageUrl,
                                       Integer level, String leftTitle, String rightTitle) {
        String friendId = friendship.getUserId().equals(currentUserId)
            ? friendship.getFriendId()
            : friendship.getUserId();

        return FriendResponse.builder()
            .friendshipId(friendship.getId())
            .friendId(friendId)
            .friendNickname(friendNickname)
            .friendProfileImageUrl(profileImageUrl)
            .friendLevel(level)
            .friendTitle(leftTitle)
            .friendTitleLeft(leftTitle)
            .friendTitleRight(rightTitle)
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
