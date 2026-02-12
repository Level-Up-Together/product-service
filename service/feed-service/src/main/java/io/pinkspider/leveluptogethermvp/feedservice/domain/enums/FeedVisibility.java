package io.pinkspider.leveluptogethermvp.feedservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedVisibility {
    PUBLIC("전체 공개"),
    FRIENDS("친구만"),
    GUILD("길드만"),
    PRIVATE("비공개");

    private final String displayName;
}
