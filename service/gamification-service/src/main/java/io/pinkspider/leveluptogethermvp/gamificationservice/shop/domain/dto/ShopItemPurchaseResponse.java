package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** LUT-327: 아이템 구매 결과 — 차감 가격과 차감 후 다이아 잔액 */
@JsonNaming(SnakeCaseStrategy.class)
public record ShopItemPurchaseResponse(
        Long shopItemId,
        Integer price,
        Integer balance) {

    public static ShopItemPurchaseResponse of(Long shopItemId, Integer price, int balance) {
        return new ShopItemPurchaseResponse(shopItemId, price, balance);
    }
}
