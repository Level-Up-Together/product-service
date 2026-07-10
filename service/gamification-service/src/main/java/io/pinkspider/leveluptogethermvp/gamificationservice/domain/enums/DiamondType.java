package io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * QA-220: 다이아 획득/사용 유형
 */
@Getter
@RequiredArgsConstructor
public enum DiamondType {

    LEVEL_UP("레벨업"),
    MISSION_BOOK("미션북"),
    SHOP("상점 사용");

    private final String description;
}
