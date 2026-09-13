package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LUT-451: 스토어 구독 영수증 검증 결과.
 *
 * @param storeProductId 스토어 상품 ID (검증 시 스토어 응답 기준)
 * @param basePlanId Android base plan ID — iOS는 null
 * @param originalTransactionId iOS originalTransactionId (웹훅 매칭 키) — Android는 null
 * @param purchaseToken Android purchaseToken (웹훅 매칭 키) — iOS는 null
 * @param startedAt 최초 구매 시각 — 확보 못하면 null (grant 시각으로 대체)
 * @param expiresAt 만료 시각 — 검증 비활성 모드면 null (플랜 기본 기간으로 대체)
 * @param autoRenew 자동갱신 여부 (iOS 트랜잭션 payload에는 없어 기본 true — LUT-452 웹훅이 정정)
 * @param trial 무료 체험/introductory offer 사용 구매 여부
 * @param transactionId 이 결제 건의 트랜잭션 ID (iOS transactionId) — 결제 이력 추적용 (LUT-486)
 * @param priceAmount 결제 금액 — iOS JWS payload만 제공, Android/검증 비활성은 null (LUT-486)
 * @param priceCurrency 결제 통화 (ISO 4217) — priceAmount 와 짝
 */
public record SubscriptionVerificationResult(
        String storeProductId,
        String basePlanId,
        String originalTransactionId,
        String purchaseToken,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        boolean autoRenew,
        boolean trial,
        String transactionId,
        BigDecimal priceAmount,
        String priceCurrency) {

    /** 결제건 식별자·가격 미확보 경로(Google·검증 비활성 모드)용 — LUT-486 이전 시그니처 유지 */
    public SubscriptionVerificationResult(
            String storeProductId,
            String basePlanId,
            String originalTransactionId,
            String purchaseToken,
            LocalDateTime startedAt,
            LocalDateTime expiresAt,
            boolean autoRenew,
            boolean trial) {
        this(
                storeProductId,
                basePlanId,
                originalTransactionId,
                purchaseToken,
                startedAt,
                expiresAt,
                autoRenew,
                trial,
                null,
                null,
                null);
    }
}
