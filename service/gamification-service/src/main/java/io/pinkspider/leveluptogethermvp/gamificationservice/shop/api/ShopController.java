package io.pinkspider.leveluptogethermvp.gamificationservice.shop.api;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ShopService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemPurchaseResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** LUT-327: 상점 — 판매 아이템 목록 조회/구매 */
@RestController
@RequestMapping("/api/v1/shop-items")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    // 판매중 아이템 목록 (희귀도→가격→ID 오름차순, 보유 여부 포함)
    @GetMapping
    public ResponseEntity<ApiResult<List<ShopItemResponse>>> getShopItems(
        @CurrentUser String userId) {
        List<ShopItemResponse> responses = shopService.getShopItems(userId);
        return ResponseEntity.ok(ApiResult.<List<ShopItemResponse>>builder().value(responses).build());
    }

    // 아이템 구매 (다이아 차감 + 인벤토리 지급)
    @PostMapping("/{shopItemId}/purchase")
    public ResponseEntity<ApiResult<ShopItemPurchaseResponse>> purchaseItem(
        @CurrentUser String userId,
        @PathVariable Long shopItemId) {
        ShopItemPurchaseResponse response = shopService.purchaseItem(userId, shopItemId);
        return ResponseEntity.ok(ApiResult.<ShopItemPurchaseResponse>builder().value(response).build());
    }
}
