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
 * @param trialUsed 무료 체험 사용 여부 — <b>유저 평생 이력</b>("체험을 쓴 적 있다"). 체험을 소진한 유저가
 *     정가로 재구독해도 true 이므로 "이번 결제가 체험으로 시작됐는가"의 판정에 쓰면 안 된다 (LUT-500)
 * @param trial LUT-500: <b>이번 검증 결제 건</b>이 무료 체험으로 시작됐는지 — 결제 원장
 *     {@code subscription_payment_history.trial}(LUT-486)과 같은 값. {@code /verify} 응답에만 실리고
 *     {@code /me} 응답은 null (조회는 결제 건이 아니다)
 */
public record SubscriptionEntitlementResponse(
        SubscriptionStatus status,
        Boolean isEntitled,
        SubscriptionPlan plan,
        LocalDateTime expiresAt,
        LocalDateTime gracePeriodExpiresAt,
        Boolean autoRenew,
        Boolean trialUsed,
        Boolean trial) {

    /** LUT-500 이전 시그니처 유지 — 결제 건 정보가 없는 조회 응답용 (trial=null) */
    public SubscriptionEntitlementResponse(
            SubscriptionStatus status,
            Boolean isEntitled,
            SubscriptionPlan plan,
            LocalDateTime expiresAt,
            LocalDateTime gracePeriodExpiresAt,
            Boolean autoRenew,
            Boolean trialUsed) {
        this(status, isEntitled, plan, expiresAt, gracePeriodExpiresAt, autoRenew, trialUsed, null);
    }

    /** 구독 이력 없음 */
    public static SubscriptionEntitlementResponse none() {
        return new SubscriptionEntitlementResponse(
                SubscriptionStatus.NONE, false, null, null, null, false, false);
    }

    /** 조회 응답 — 결제 건 정보 없음 (trial=null) */
    public static SubscriptionEntitlementResponse of(
            UserSubscription subscription, LocalDateTime now) {
        return of(subscription, now, null);
    }

    /** LUT-500: 검증(결제) 응답 — 이번 결제 건의 무료 체험 여부를 함께 싣는다 */
    public static SubscriptionEntitlementResponse of(
            UserSubscription subscription, LocalDateTime now, Boolean trial) {
        SubscriptionStatus status = subscription.resolveStatus(now);
        return new SubscriptionEntitlementResponse(
                status,
                status.isEntitled(),
                subscription.getPlan(),
                subscription.getExpiresAt(),
                subscription.getGracePeriodExpiresAt(),
                subscription.getAutoRenew(),
                subscription.getTrialUsed(),
                trial);
    }
}
