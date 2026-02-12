package io.pinkspider.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportTargetType {
    USER_PROFILE("사용자 프로필"),
    FEED("피드"),
    FEED_COMMENT("피드 댓글"),
    GUILD("길드"),
    GUILD_NOTICE("길드 공지"),
    MISSION("미션"),
    MISSION_COMMENT("미션 댓글");

    private final String description;
}
