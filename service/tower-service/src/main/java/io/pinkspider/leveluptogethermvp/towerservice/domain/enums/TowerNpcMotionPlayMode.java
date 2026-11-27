package io.pinkspider.leveluptogethermvp.towerservice.domain.enums;

/**
 * LUT-864: NPC 움직이는 그림 재생 방식 — LOOP = 쉬지 않고 되풀이, OCCASIONAL = 첫 장으로 서 있다가 {@code interval_seconds}
 * 마다 한 번(기본 — 계속 움직이면 정신없다).
 */
public enum TowerNpcMotionPlayMode {
    LOOP,
    OCCASIONAL
}
