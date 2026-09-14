package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** LUT-486: 어드민 구독 결제 이력 행 응답 — nickname 은 목록 화면용 벌크 조회 (LUT-488) */
@JsonNaming(SnakeCaseStrategy.class)
public record SubscriptionPaymentHistoryResponse(
        Long id,
        String userId,
        String nickname,
        String platform,
        String productId,
        String basePlanId,
        SubscriptionPlan plan,
        SubscriptionPaymentEventType eventType,
        boolean trial,
        BigDecimal priceAmount,
        String priceCurrency,
        String transactionId,
        LocalDateTime expiresAt,
        LocalDateTime occurredAt) {

    public static SubscriptionPaymentHistoryResponse from(
            SubscriptionPaymentHistory history, String nickname) {
        return new SubscriptionPaymentHistoryResponse(
                history.getId(),
                history.getUserId(),
                nickname,
                history.getPlatform(),
                history.getProductId(),
                history.getBasePlanId(),
                history.getPlan(),
                history.getEventType(),
                Boolean.TRUE.equals(history.getTrial()),
                history.getPriceAmount(),
                history.getPriceCurrency(),
                history.getTransactionId(),
                history.getExpiresAt(),
                history.getOccurredAt());
    }
}
