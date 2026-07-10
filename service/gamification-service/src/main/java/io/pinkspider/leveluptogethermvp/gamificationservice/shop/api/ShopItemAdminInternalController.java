package io.pinkspider.leveluptogethermvp.gamificationservice.shop.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ShopItemAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemImageUploadResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin 내부 API 컨트롤러 - 상점 아이템 (QA-225)
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/shop-items")
@RequiredArgsConstructor
public class ShopItemAdminInternalController {

    private final ShopItemAdminService shopItemAdminService;

    @GetMapping
    public ApiResult<ShopItemAdminPageResponse> searchShopItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "item_type", required = false) ShopItemType itemType,
            @RequestParam(required = false) TitleRarity rarity,
            @RequestParam(name = "is_active", required = false) Boolean isActive,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "sort_by", required = false, defaultValue = "id") String sortBy,
            @RequestParam(name = "sort_direction", required = false, defaultValue = "DESC") String sortDirection) {
        Sort sort = "ASC".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        return ApiResult.<ShopItemAdminPageResponse>builder()
            .value(shopItemAdminService.searchShopItems(
                keyword, itemType, rarity, isActive, PageRequest.of(page, size, sort)))
            .build();
    }

    @GetMapping("/{id}")
    public ApiResult<ShopItemAdminResponse> getShopItem(@PathVariable Long id) {
        return ApiResult.<ShopItemAdminResponse>builder()
            .value(shopItemAdminService.getShopItem(id))
            .build();
    }

    @PostMapping
    public ApiResult<ShopItemAdminResponse> createShopItem(@Valid @RequestBody ShopItemAdminRequest request) {
        return ApiResult.<ShopItemAdminResponse>builder()
            .value(shopItemAdminService.createShopItem(request))
            .build();
    }

    @PutMapping("/{id}")
    public ApiResult<ShopItemAdminResponse> updateShopItem(
            @PathVariable Long id,
            @Valid @RequestBody ShopItemAdminRequest request) {
        return ApiResult.<ShopItemAdminResponse>builder()
            .value(shopItemAdminService.updateShopItem(id, request))
            .build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ApiResult<ShopItemAdminResponse> toggleActiveStatus(@PathVariable Long id) {
        return ApiResult.<ShopItemAdminResponse>builder()
            .value(shopItemAdminService.toggleActiveStatus(id))
            .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteShopItem(@PathVariable Long id) {
        shopItemAdminService.deleteShopItem(id);
        return ApiResult.<Void>builder().build();
    }

    @PostMapping("/images")
    public ApiResult<ShopItemImageUploadResponse> uploadImage(@RequestPart("file") MultipartFile file) {
        return ApiResult.<ShopItemImageUploadResponse>builder()
            .value(new ShopItemImageUploadResponse(shopItemAdminService.uploadImage(file)))
            .build();
    }
}
