package io.pinkspider.leveluptogethermvp.feedservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedSearchType {
    ALL("전체"),
    FRIENDS("친구"),
    GUILD("길드"),
    MINE("내 글");

    private final String displayName;
}
