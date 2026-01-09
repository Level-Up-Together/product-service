package io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TitlePosition {
    LEFT("좌측"),
    RIGHT("우측");

    private final String description;
}
