package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;

/**
 * LUT-617: 어드민 내부 API — Android 구독 원장을 Play 주문 연번과 대조한 결과.
 *
 * @param userId 대상 유저
 * @param latestOrderId Play 가 돌려준 최신 주문 ID
 * @param subscriptionState Play 구독 상태 원문
 * @param expiresAt Play 기준 만료
 * @param rowsBefore 대조 전 이 유저의 원장 행 수
 * @param rowsAfter 대조 후 행 수
 * @param rowsWritten 새로 쓰거나 정정한 행 수
 */
@JsonNaming(SnakeCaseStrategy.class)
public record SubscriptionLedgerReconcileResponse(
        String userId,
        String latestOrderId,
        String subscriptionState,
        LocalDateTime expiresAt,
        long rowsBefore,
        long rowsAfter,
        int rowsWritten) {}
