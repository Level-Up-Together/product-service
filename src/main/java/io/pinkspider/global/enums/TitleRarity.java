package io.pinkspider.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TitleRarity {
    COMMON("일반", null),
    UNCOMMON("고급", "#20F907"),
    RARE("희귀", "#00F9FF"),
    EPIC("영웅", "#FF4FF0"),
    LEGENDARY("전설", "#FFD809"),
    MYTHIC("신화", "#FF5C00");

    private final String name;
    private final String colorCode;
}
