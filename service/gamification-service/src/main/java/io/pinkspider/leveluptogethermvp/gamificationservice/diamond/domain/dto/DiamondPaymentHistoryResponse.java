package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.enums.DiamondPurchaseStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** LUT-401: 어드민 다이아 결제이력 행 응답 */
@JsonNaming(SnakeCaseStrategy.class)
public record DiamondPaymentHistoryResponse(
        Long id,
        String userId,
        String nickname,
        Long bundleId,
        String bundleName,
        String platform,
        String storeProductId,
        String storeTransactionId,
        Integer diamondCount,
        BigDecimal priceAmount,
        String priceCurrency,
        DiamondPurchaseStatus status,
        LocalDateTime refundedAt,
        LocalDateTime purchasedAt) {

    public static DiamondPaymentHistoryResponse from(DiamondPaymentHistoryRow row, String nickname) {
        return new DiamondPaymentHistoryResponse(
            row.id(),
            row.userId(),
            nickname,
            row.bundleId(),
            row.bundleName(),
            row.platform(),
            row.storeProductId(),
            row.storeTransactionId(),
            row.diamondCount(),
            row.priceAmount(),
            row.priceCurrency(),
            row.status(),
            row.refundedAt(),
            row.purchasedAt());
    }
}
