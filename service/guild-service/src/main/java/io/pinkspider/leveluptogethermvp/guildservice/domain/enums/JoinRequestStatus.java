package io.pinkspider.leveluptogethermvp.guildservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JoinRequestStatus {
    PENDING("대기중"),
    APPROVED("승인"),
    REJECTED("거절"),
    CANCELLED("취소");

    private final String description;
}
