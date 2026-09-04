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
    SHOP("상점 사용"),
    PINK_PURCHASE("핑크다이아 구매"),
    // LUT-453: 원장 source 태그 — 구독분 발행/소진 집계·추후 별도 재화 승격의 근거라 반드시 구분 기록
    SUBSCRIPTION("구독 스티펜드");

    private final String description;
}
