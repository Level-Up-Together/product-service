package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import io.pinkspider.global.enums.TitleRarity;
import java.time.LocalDateTime;

/**
 * LUT-328: 구매이력 조회 JPQL 프로젝션 (diamond_history SHOP ⋈ shop_item).
 * amount 는 원장 기준 음수(차감)이며, 응답 변환 시 가격으로 부호 반전한다.
 */
public record ShopPurchaseHistoryRow(
        Long historyId,
        String userId,
        Integer amount,
        LocalDateTime purchasedAt,
        Long shopItemId,
        String itemName,
        TitleRarity rarity) {
}
