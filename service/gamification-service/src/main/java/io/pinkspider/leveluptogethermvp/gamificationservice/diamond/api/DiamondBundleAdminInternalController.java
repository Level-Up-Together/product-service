package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.DiamondBundleAdminService;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundleAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto.ShopItemImageUploadResponse;
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
 * Admin 내부 API 컨트롤러 - 핑크다이아 묶음상품 (LUT-356)
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll — InternalApiKeyFilter가 헤더 인증)
 */
@RestController
@RequestMapping("/api/internal/diamond-bundles")
@RequiredArgsConstructor
public class DiamondBundleAdminInternalController {

    private final DiamondBundleAdminService diamondBundleAdminService;

    @GetMapping
    public ApiResult<DiamondBundleAdminPageResponse> searchBundles(
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "is_active", required = false) Boolean isActive,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "sort_by", required = false, defaultValue = "id") String sortBy,
            @RequestParam(name = "sort_direction", required = false, defaultValue = "DESC") String sortDirection) {
        Sort sort = "ASC".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        return ApiResult.<DiamondBundleAdminPageResponse>builder()
            .value(diamondBundleAdminService.searchBundles(
                keyword, isActive, PageRequest.of(page, size, sort)))
            .build();
    }

    @GetMapping("/{id}")
    public ApiResult<DiamondBundleAdminResponse> getBundle(@PathVariable Long id) {
        return ApiResult.<DiamondBundleAdminResponse>builder()
            .value(diamondBundleAdminService.getBundle(id))
            .build();
    }

    @PostMapping
    public ApiResult<DiamondBundleAdminResponse> createBundle(
            @Valid @RequestBody DiamondBundleAdminRequest request) {
        return ApiResult.<DiamondBundleAdminResponse>builder()
            .value(diamondBundleAdminService.createBundle(request))
            .build();
    }

    @PutMapping("/{id}")
    public ApiResult<DiamondBundleAdminResponse> updateBundle(
            @PathVariable Long id,
            @Valid @RequestBody DiamondBundleAdminRequest request) {
        return ApiResult.<DiamondBundleAdminResponse>builder()
            .value(diamondBundleAdminService.updateBundle(id, request))
            .build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ApiResult<DiamondBundleAdminResponse> toggleActiveStatus(@PathVariable Long id) {
        return ApiResult.<DiamondBundleAdminResponse>builder()
            .value(diamondBundleAdminService.toggleActiveStatus(id))
            .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteBundle(@PathVariable Long id) {
        diamondBundleAdminService.deleteBundle(id);
        return ApiResult.<Void>builder().build();
    }

    @PostMapping("/images")
    public ApiResult<ShopItemImageUploadResponse> uploadImage(
            @RequestPart("file") MultipartFile file) {
        return ApiResult.<ShopItemImageUploadResponse>builder()
            .value(new ShopItemImageUploadResponse(diamondBundleAdminService.uploadImage(file)))
            .build();
    }
}
