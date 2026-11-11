package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums;

/** LUT-779: 판정이 어느 두 그룹을 비교했는가 — NOT_ENOUGH_DATA 가 어느 비교의 것인지 화면이 알 수 있게 */
public enum VerdictComparison {
    /** ① 혼자 써도 남는가: SOLO vs NOT_COMPLETED */
    SOLO_VS_NOT_COMPLETED,
    /** ② 함께하면 더 남는가: SOCIAL vs SOLO */
    SOCIAL_VS_SOLO
}
