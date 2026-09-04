package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionStatus;
import java.time.LocalDateTime;

/**
 * 구독 권한(entitlement) 조회 응답 (LUT-450)
 *
 * <p>프론트 구독 상태의 단일 출처. 구독 이력이 없으면 {@code status=NONE}에 나머지 필드는 null/false.
 *
 * @param status 구독 상태 (NONE|ACTIVE|GRACE_PERIOD|EXPIRED)
 * @param isEntitled 구독 권한 보유 여부 (ACTIVE 또는 GRACE_PERIOD)
 * @param plan 내부 플랜 (MONTHLY|ANNUAL) — NONE이면 null
 * @param expiresAt 만료 시각 — NONE이면 null
 * @param gracePeriodExpiresAt 유예기간 종료 시각 — 유예 중이 아니면 null
 * @param autoRenew 자동갱신 여부
 * @param trialUsed 무료 체험 사용 여부
 */
public record SubscriptionEntitlementResponse(
        SubscriptionStatus status,
        Boolean isEntitled,
        SubscriptionPlan plan,
        LocalDateTime expiresAt,
        LocalDateTime gracePeriodExpiresAt,
        Boolean autoRenew,
        Boolean trialUsed) {

    /** 구독 이력 없음 */
    public static SubscriptionEntitlementResponse none() {
        return new SubscriptionEntitlementResponse(
                SubscriptionStatus.NONE, false, null, null, null, false, false);
    }

    public static SubscriptionEntitlementResponse of(
            UserSubscription subscription, LocalDateTime now) {
        SubscriptionStatus status = subscription.resolveStatus(now);
        return new SubscriptionEntitlementResponse(
                status,
                status.isEntitled(),
                subscription.getPlan(),
                subscription.getExpiresAt(),
                subscription.getGracePeriodExpiresAt(),
                subscription.getAutoRenew(),
                subscription.getTrialUsed());
    }
}
