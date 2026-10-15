package io.pinkspider.leveluptogethermvp.gamificationservice.shop.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ItemPushSettingAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemPushSettingRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemPushSettingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - 장착 아이템 푸시 발송 설정 (LUT-528). 아이템 단위 발송 시각을 관리한다. 기존 shop-items 리소스 하위에 중첩. 인증
 * 불필요 (SecurityConfig 에서 /api/internal/** permitAll — InternalApiKeyFilter 가 방어). HEAD 검증은 서비스에서
 * 수행.
 */
@RestController
@RequestMapping("/api/internal/shop-items/{itemId}/push-setting")
@RequiredArgsConstructor
public class ItemPushSettingAdminInternalController {

    private final ItemPushSettingAdminService itemPushSettingAdminService;

    @GetMapping
    public ApiResult<ItemPushSettingResponse> get(@PathVariable Long itemId) {
        return ApiResult.<ItemPushSettingResponse>builder()
                .value(itemPushSettingAdminService.get(itemId))
                .build();
    }

    @PutMapping
    public ApiResult<ItemPushSettingResponse> upsert(
            @PathVariable Long itemId,
            @Valid @RequestBody ItemPushSettingRequest request,
            @RequestHeader("X-Admin-Id") Long adminId) {
        return ApiResult.<ItemPushSettingResponse>builder()
                .value(itemPushSettingAdminService.upsert(itemId, request, adminId))
                .build();
    }

    @DeleteMapping
    public ApiResult<Void> delete(@PathVariable Long itemId) {
        itemPushSettingAdminService.delete(itemId);
        return ApiResult.<Void>builder().build();
    }
}
