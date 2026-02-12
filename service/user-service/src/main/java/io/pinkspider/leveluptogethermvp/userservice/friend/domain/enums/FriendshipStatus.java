package io.pinkspider.leveluptogethermvp.userservice.friend.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FriendshipStatus {
    PENDING("대기중"),
    ACCEPTED("수락됨"),
    REJECTED("거절됨"),
    BLOCKED("차단됨");

    private final String displayName;
}
