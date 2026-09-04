package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import java.time.LocalDateTime;

/**
 * LUT-452: Play Developer API subscriptionsv2 로 조회한 구독 현재 상태.
 *
 * <p>영수증 검증(LUT-451)과 RTDN 웹훅이 공유한다 — RTDN 은 트리거일 뿐이고 상태의 진실은 항상 이
 * 재조회 결과다(페이로드 위조 방어 겸용).
 *
 * @param productId 스토어 상품 ID
 * @param basePlanId base plan ID (1m|1y)
 * @param startedAt 최초 시작 시각 — 없으면 null
 * @param expiresAt 만료 시각 (Google 유예기간 중에는 유예를 반영한 값)
 * @param autoRenew 자동갱신 여부
 * @param trial 오퍼(무료 체험) 적용 구매 여부
 * @param subscriptionState 원문 상태 (예: SUBSCRIPTION_STATE_ACTIVE|_IN_GRACE_PERIOD|_CANCELED)
 */
public record GoogleSubscriptionState(
        String productId,
        String basePlanId,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        boolean autoRenew,
        boolean trial,
        String subscriptionState) {

    public static final String STATE_PENDING = "SUBSCRIPTION_STATE_PENDING";
    public static final String STATE_IN_GRACE_PERIOD = "SUBSCRIPTION_STATE_IN_GRACE_PERIOD";

    public boolean isPending() {
        return STATE_PENDING.equals(subscriptionState);
    }

    public boolean isInGracePeriod() {
        return STATE_IN_GRACE_PERIOD.equals(subscriptionState);
    }
}
