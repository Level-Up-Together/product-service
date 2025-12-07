package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TitleRarity {
    COMMON("일반", "#FFFFFF"),
    UNCOMMON("고급", "#1EFF00"),
    RARE("희귀", "#0070DD"),
    EPIC("영웅", "#A335EE"),
    LEGENDARY("전설", "#FF8000");

    private final String name;
    private final String colorCode;
}
