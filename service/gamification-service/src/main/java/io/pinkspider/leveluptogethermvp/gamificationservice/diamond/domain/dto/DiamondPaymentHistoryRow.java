package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.enums.DiamondPurchaseStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LUT-401: 어드민 다이아 결제이력 조회 JPQL 프로젝션 (diamond_bundle_purchase ⋈ diamond_bundle).
 */
public record DiamondPaymentHistoryRow(
        Long id,
        String userId,
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
}
