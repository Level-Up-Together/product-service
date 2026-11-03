package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

/**
 * LUT-601: 하루치 구독 지표 원천 집계 (gamification_db).
 *
 * @param newSubscriptions PURCHASE 이벤트 수
 * @param trialStarts PURCHASE ∧ trial
 * @param trialConversions 체험으로 시작한 구독의 첫 유료 RENEWAL 이 이날
 * @param refunds REFUND 이벤트 수
 * @param activeSubscriptions 그날 끝(KST 자정) 기준 만료되지 않은 구독 행 수(유예 포함)
 * @param expiredNotRenewed 이날 만료됐고 이후 연장되지 않은 구독 행 수
 */
public record SubscriptionDailyCounts(
        int newSubscriptions,
        int trialStarts,
        int trialConversions,
        int refunds,
        int activeSubscriptions,
        int expiredNotRenewed) {

    public static final SubscriptionDailyCounts ZERO =
            new SubscriptionDailyCounts(0, 0, 0, 0, 0, 0);

    /** 이탈 = 연장 없이 만료 + 환불 */
    public int churned() {
        return expiredNotRenewed + refunds;
    }
}
