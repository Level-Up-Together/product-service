package io.pinkspider.leveluptogethermvp.towerservice.domain.enums;

/**
 * LUT-733: NPC 창 이동 링크 종류 — 상점 아이템(판매 중만) / 이벤트 / 멤버십 구독 / 앱 안 경로({@code /} 로 시작). 앱 경로 변환은 클라이언트가
 * 한다.
 */
public enum TowerNpcCardLinkType {
    SHOP_ITEM,
    EVENT,
    SUBSCRIPTION,
    PATH
}
