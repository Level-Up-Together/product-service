package io.pinkspider.leveluptogethermvp.gamificationservice.shop.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ShopPurchaseHistoryAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopPurchaseHistoryAdminPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-328: Admin 내부 API 컨트롤러 - 아이템 구매이력
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll + InternalApiKeyFilter)
 */
@RestController
@RequestMapping("/api/internal/shop-purchases")
@RequiredArgsConstructor
public class ShopPurchaseHistoryAdminInternalController {

    private final ShopPurchaseHistoryAdminService shopPurchaseHistoryAdminService;

    // 구매이력 목록 — keyword 는 아이템명(한/영) OR 구매자 닉네임 매칭, 최신순
    @GetMapping
    public ApiResult<ShopPurchaseHistoryAdminPageResponse> getPurchaseHistory(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ApiResult.<ShopPurchaseHistoryAdminPageResponse>builder()
            .value(shopPurchaseHistoryAdminService.getPurchaseHistory(keyword, page, size))
            .build();
    }
}
