package io.pinkspider.leveluptogethermvp.towerservice.domain.enums;

/**
 * LUT-725: 칠한 영역 안에서만 그리는 효과 레이어 — 눈 내리기 / 별 반짝임 (층마다 종류당 1개). LUT-740: 구름(안개) — 칠한 영역 위로 흐르고 바깥으로
 * 옅게 번진다(spread).
 */
public enum TowerEffectKind {
    SNOW,
    STARS,
    CLOUD
}
