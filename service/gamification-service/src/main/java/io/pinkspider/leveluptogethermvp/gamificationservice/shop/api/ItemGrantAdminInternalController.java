package io.pinkspider.leveluptogethermvp.gamificationservice.shop.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ItemGrantAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemGrantAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemGrantAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemGrantAdminResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - 아이템 부여 (LUT-472)
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll — InternalApiKeyFilter가 방어)
 */
@RestController
@RequestMapping("/api/internal/item-grants")
@RequiredArgsConstructor
public class ItemGrantAdminInternalController {

    private final ItemGrantAdminService itemGrantAdminService;

    @PostMapping
    public ApiResult<ItemGrantAdminResponse> grantItem(
        @Valid @RequestBody ItemGrantAdminRequest request,
        @RequestHeader("X-Admin-Id") Long adminId) {
        return ApiResult.<ItemGrantAdminResponse>builder()
            .value(itemGrantAdminService.grantItem(request, adminId))
            .build();
    }

    @DeleteMapping("/{itemGrantId}")
    public ApiResult<Void> revokeItem(
        @PathVariable Long itemGrantId,
        @RequestHeader("X-Admin-Id") Long adminId) {
        itemGrantAdminService.revokeItem(itemGrantId, adminId);
        return ApiResult.<Void>builder().build();
    }

    @GetMapping
    public ApiResult<ItemGrantAdminPageResponse> getGrantHistory(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<ItemGrantAdminPageResponse>builder()
            .value(itemGrantAdminService.getGrantHistory(keyword, page, size))
            .build();
    }
}
