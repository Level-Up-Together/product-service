package io.pinkspider.leveluptogethermvp.userservice.home.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LinkType {
    GUILD("길드 상세"),
    MISSION("미션 상세"),
    EXTERNAL("외부 링크"),
    INTERNAL("내부 링크");

    private final String displayName;
}
