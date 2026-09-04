package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums;

/**
 * 내부 구독 플랜 (LUT-450)
 *
 * <p>스토어별 상품 구조 차이는 {@code SubscriptionPlanMapping}이 흡수하며, RN·웹·분석·CS 등
 * 이 뒤의 모든 소비자는 이 두 값만 본다 — 프론트 어디에도 플랫폼 분기가 생기지 않게 한다.
 */
public enum SubscriptionPlan {
    MONTHLY,
    ANNUAL
}
