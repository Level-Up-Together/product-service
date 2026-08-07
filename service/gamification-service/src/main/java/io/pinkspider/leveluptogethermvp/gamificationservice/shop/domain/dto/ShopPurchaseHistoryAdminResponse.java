package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.TitleRarity;
import java.time.LocalDateTime;

/** LUT-328: 어드민 아이템 구매이력 행 응답 */
@JsonNaming(SnakeCaseStrategy.class)
public record ShopPurchaseHistoryAdminResponse(
        Long id,
        Long shopItemId,
        String itemName,
        TitleRarity rarity,
        String rarityName,
        String rarityColorCode,
        Integer price,
        String userId,
        String nickname,
        LocalDateTime purchasedAt) {

    public static ShopPurchaseHistoryAdminResponse from(
            ShopPurchaseHistoryRow row, String nickname) {
        return new ShopPurchaseHistoryAdminResponse(
            row.historyId(),
            row.shopItemId(),
            row.itemName(),
            row.rarity(),
            row.rarity() != null ? row.rarity().getName() : null,
            row.rarity() != null ? row.rarity().getColorCode() : null,
            // 원장 amount 는 차감이 음수 (0원 구매는 0) — 가격은 부호 반전
            row.amount() != null ? -row.amount() : null,
            row.userId(),
            nickname,
            row.purchasedAt());
    }
}
