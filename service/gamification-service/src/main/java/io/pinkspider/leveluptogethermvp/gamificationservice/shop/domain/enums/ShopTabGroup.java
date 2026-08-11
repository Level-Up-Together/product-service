package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums;

/**
 * LUT-349: 상점 화면의 탭 구분 — 해금 판정 단위다.
 *
 * <p>프론트가 탭(날개/기타)마다 희귀도 섹션을 따로 그리므로, "각 등급 최저가 N개 해금"의 N 도
 * 탭별로 세어야 화면과 맞는다. 등급만으로 세면 한쪽 탭의 해금 슬롯을 다른 탭 아이템이 가져가
 * 어떤 탭에서는 상위 등급이 통째로 잠겨 보일 수 있다.
 *
 * <p>구분 기준은 프론트 WINGS_TYPES(shop/page.tsx)와 같아야 한다.
 */
public enum ShopTabGroup {
    /** 날개 탭 — 캐릭터 몸 영역을 쓰는 타입 */
    WINGS,
    /** 기타 탭 — 그 외 전부 */
    ETC;

    public static ShopTabGroup from(ShopItemType itemType) {
        if (itemType == null) {
            return ETC;
        }
        return switch (itemType) {
            case BASIC, FULL -> WINGS;
            default -> ETC;
        };
    }
}
