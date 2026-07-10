package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 상점 아이템 타입 (QA-225)
 */
@Getter
@RequiredArgsConstructor
public enum ShopItemType {
    BASIC("기본"),
    FULL("풀"),
    HEAD("머리"),
    EFFECT("이펙트");

    private final String displayName;
}
