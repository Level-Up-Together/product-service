package io.pinkspider.leveluptogethermvp.towerservice.domain.enums;

/**
 * LUT-725: 칠한 영역 안에서만 그리는 효과 레이어 — 눈 내리기 / 별 반짝임 (층마다 종류당 1개). LUT-740: 구름(안개) — 칠한 영역 위로 흐르고 바깥으로
 * 옅게 번진다(spread). LUT-785: 물방울(유리창 빗물) — 칠한 유리 영역에 빗물이 맺히고 흘러내린다(dropCount). LUT-838: 꽃가루 — 칠한 영역에서
 * 생겨나 흔들리며 떨어진다(color = 기준 색, 화면이 비슷한 계열 여러 색으로 그린다). LUT-839: 비 내림 — 칠한 영역 안에서 빗줄기가 일자 또는 사선으로
 * 내린다(direction·angle). 유리창에 맺히는 RAIN 과 다른 효과다. LUT-863: 반딧불 — 칠한 영역에서 생겨나 흔들리며 천천히 위로 떠오르고
 * 깜빡인다(color = 빛 색, size = 한 마리 지름).
 */
public enum TowerEffectKind {
    SNOW,
    STARS,
    CLOUD,
    RAIN,
    POLLEN,
    RAINFALL,
    FIREFLY
}
