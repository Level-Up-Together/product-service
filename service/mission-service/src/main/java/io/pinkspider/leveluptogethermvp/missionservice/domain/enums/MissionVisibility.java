package io.pinkspider.leveluptogethermvp.missionservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MissionVisibility {

    PUBLIC("public", "공개"),
    FRIENDS_ONLY("friends_only", "친구 공개"),
    PRIVATE("private", "비공개"),
    GUILD_ONLY("guild_only", "길드 전용"),
    // LUT-257: 친구 공개 + 길드 공개 동시 선택 (varchar(20) 내 수용)
    FRIENDS_AND_GUILD("friends_and_guild", "친구+길드 공개");

    private final String code;
    private final String description;
}
