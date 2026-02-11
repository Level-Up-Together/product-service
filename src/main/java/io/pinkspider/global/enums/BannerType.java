package io.pinkspider.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BannerType {
    GUILD_RECRUIT("길드 모집"),
    EVENT("이벤트"),
    NOTICE("공지사항"),
    AD("광고");

    private final String displayName;
}
