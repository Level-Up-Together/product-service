package io.pinkspider.leveluptogethermvp.towerservice.domain.enums;

/** LUT-705: 방문객 제재 종류 — CHAT 은 채팅·리액션만, ENTRY 는 입장(그리고 채팅도) 금지 */
public enum TowerVisitorSanctionType {
    CHAT,
    ENTRY;

    /** 이 제재가 {@code wanted} 를 막는가 — 입장 금지는 채팅도 막는다 */
    public boolean blocks(TowerVisitorSanctionType wanted) {
        return this == wanted || this == ENTRY;
    }
}
