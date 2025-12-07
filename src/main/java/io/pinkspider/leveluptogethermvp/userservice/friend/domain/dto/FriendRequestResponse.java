package io.pinkspider.leveluptogethermvp.userservice.friend.domain.dto;

import io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity.Friendship;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestResponse {
    private Long id;
    private String requesterId;
    private String requesterNickname;
    private String requesterProfileImageUrl;
    private Integer requesterLevel;
    private String message;
    private LocalDateTime requestedAt;

    public static FriendRequestResponse from(Friendship friendship,
                                              String requesterNickname,
                                              String profileImageUrl,
                                              Integer level) {
        return FriendRequestResponse.builder()
            .id(friendship.getId())
            .requesterId(friendship.getUserId())
            .requesterNickname(requesterNickname)
            .requesterProfileImageUrl(profileImageUrl)
            .requesterLevel(level)
            .message(friendship.getMessage())
            .requestedAt(friendship.getRequestedAt())
            .build();
    }

    public static FriendRequestResponse simpleFrom(Friendship friendship) {
        return FriendRequestResponse.builder()
            .id(friendship.getId())
            .requesterId(friendship.getUserId())
            .message(friendship.getMessage())
            .requestedAt(friendship.getRequestedAt())
            .build();
    }
}
