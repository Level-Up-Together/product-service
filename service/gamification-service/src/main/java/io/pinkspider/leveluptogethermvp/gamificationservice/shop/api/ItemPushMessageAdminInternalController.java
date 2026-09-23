package io.pinkspider.leveluptogethermvp.gamificationservice.shop.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ItemPushMessageAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemPushMessageRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ItemPushMessageResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - 장착 아이템 개별 푸시 메시지 (LUT-516). 기존 shop-items 리소스 하위에 중첩. 인증 불필요
 * (SecurityConfig 에서 /api/internal/** permitAll — InternalApiKeyFilter 가 방어). HEAD 아이템 검증은 서비스에서 수행.
 */
@RestController
@RequestMapping("/api/internal/shop-items/{itemId}/push-messages")
@RequiredArgsConstructor
public class ItemPushMessageAdminInternalController {

    private final ItemPushMessageAdminService itemPushMessageAdminService;

    @GetMapping
    public ApiResult<List<ItemPushMessageResponse>> list(@PathVariable Long itemId) {
        return ApiResult.<List<ItemPushMessageResponse>>builder()
                .value(itemPushMessageAdminService.list(itemId))
                .build();
    }

    @PostMapping
    public ApiResult<ItemPushMessageResponse> create(
            @PathVariable Long itemId,
            @Valid @RequestBody ItemPushMessageRequest request,
            @RequestHeader("X-Admin-Id") Long adminId) {
        return ApiResult.<ItemPushMessageResponse>builder()
                .value(itemPushMessageAdminService.create(itemId, request, adminId))
                .build();
    }

    @PutMapping("/{messageId}")
    public ApiResult<ItemPushMessageResponse> update(
            @PathVariable Long itemId,
            @PathVariable Long messageId,
            @Valid @RequestBody ItemPushMessageRequest request) {
        return ApiResult.<ItemPushMessageResponse>builder()
                .value(itemPushMessageAdminService.update(itemId, messageId, request))
                .build();
    }

    @PatchMapping("/{messageId}/toggle")
    public ApiResult<ItemPushMessageResponse> toggle(
            @PathVariable Long itemId, @PathVariable Long messageId) {
        return ApiResult.<ItemPushMessageResponse>builder()
                .value(itemPushMessageAdminService.toggle(itemId, messageId))
                .build();
    }

    @DeleteMapping("/{messageId}")
    public ApiResult<Void> delete(@PathVariable Long itemId, @PathVariable Long messageId) {
        itemPushMessageAdminService.delete(itemId, messageId);
        return ApiResult.<Void>builder().build();
    }
}
