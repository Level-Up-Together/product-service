package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums;

/**
 * 구독 상태 (LUT-450)
 *
 * <ul>
 *   <li>{@link #NONE} — 구독 이력 없음
 *   <li>{@link #ACTIVE} — 만료 시각 전
 *   <li>{@link #GRACE_PERIOD} — 만료됐지만 유예기간 내 (결제 재시도 중 — 권한 유지)
 *   <li>{@link #EXPIRED} — 만료 + 유예기간 종료
 * </ul>
 */
public enum SubscriptionStatus {
    NONE,
    ACTIVE,
    GRACE_PERIOD,
    EXPIRED;

    /** 구독 권한(entitlement) 보유 여부 — 유예기간에도 권한은 유지된다. */
    public boolean isEntitled() {
        return this == ACTIVE || this == GRACE_PERIOD;
    }
}
