package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 상점 아이템 이미지 포지션 (LUT-225)
 * 캐릭터 기준 이미지를 앞(front)에 그릴지 뒤(back)에 그릴지 결정한다. 기본값 BACK.
 */
@Getter
@RequiredArgsConstructor
public enum ShopItemImagePosition {
    FRONT("앞"),
    BACK("뒤");

    private final String displayName;
}