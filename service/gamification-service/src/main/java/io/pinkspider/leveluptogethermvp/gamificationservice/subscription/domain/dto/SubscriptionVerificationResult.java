package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

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
 */
public record SubscriptionVerificationResult(
        String storeProductId,
        String basePlanId,
        String originalTransactionId,
        String purchaseToken,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        boolean autoRenew,
        boolean trial) {}
