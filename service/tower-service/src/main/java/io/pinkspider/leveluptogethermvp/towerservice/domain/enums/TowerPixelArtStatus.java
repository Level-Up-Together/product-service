package io.pinkspider.leveluptogethermvp.towerservice.domain.enums;

/** LUT-727: 픽셀아트 변환 상태 — 대기(변환 중 포함) / 완료 / 실패(재시도 1회 뒤) */
public enum TowerPixelArtStatus {
    PENDING,
    DONE,
    FAILED
}
