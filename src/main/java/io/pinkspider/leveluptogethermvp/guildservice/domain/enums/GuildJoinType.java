package io.pinkspider.leveluptogethermvp.guildservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GuildJoinType {
    OPEN("자동 가입"),
    APPROVAL_REQUIRED("승인 필요");

    private final String description;
}
