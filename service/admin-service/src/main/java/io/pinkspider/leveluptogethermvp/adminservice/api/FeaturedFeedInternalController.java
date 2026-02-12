package io.pinkspider.leveluptogethermvp.adminservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.adminservice.application.FeaturedFeedAdminService;
import io.pinkspider.leveluptogethermvp.adminservice.domain.dto.FeaturedFeedPageResponse;
import io.pinkspider.leveluptogethermvp.adminservice.domain.dto.FeaturedFeedRequest;
import io.pinkspider.leveluptogethermvp.adminservice.domain.dto.FeaturedFeedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - FeaturedFeed
 * 인증 불필요 (SecurityConfig에서 /api/internal/** permitAll)
 */
@RestController
@RequestMapping("/api/internal/featured-feeds")
@RequiredArgsConstructor
public class FeaturedFeedInternalController {

    private final FeaturedFeedAdminService featuredFeedAdminService;

    @GetMapping
    public ApiResult<FeaturedFeedPageResponse> getFeaturedFeeds(
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        if (categoryId != null) {
            return ApiResult.<FeaturedFeedPageResponse>builder()
                .value(featuredFeedAdminService.getFeaturedFeedsByCategory(categoryId, PageRequest.of(page, size)))
                .build();
        }
        return ApiResult.<FeaturedFeedPageResponse>builder()
            .value(featuredFeedAdminService.getFeaturedFeeds(PageRequest.of(page, size)))
            .build();
    }

    @GetMapping("/{id}")
    public ApiResult<FeaturedFeedResponse> getFeaturedFeed(@PathVariable Long id) {
        return ApiResult.<FeaturedFeedResponse>builder()
            .value(featuredFeedAdminService.getFeaturedFeed(id))
            .build();
    }

    @PostMapping
    public ApiResult<FeaturedFeedResponse> createFeaturedFeed(
            @Valid @RequestBody FeaturedFeedRequest request) {
        return ApiResult.<FeaturedFeedResponse>builder()
            .value(featuredFeedAdminService.createFeaturedFeed(request))
            .build();
    }

    @PutMapping("/{id}")
    public ApiResult<FeaturedFeedResponse> updateFeaturedFeed(
            @PathVariable Long id,
            @Valid @RequestBody FeaturedFeedRequest request) {
        return ApiResult.<FeaturedFeedResponse>builder()
            .value(featuredFeedAdminService.updateFeaturedFeed(id, request))
            .build();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteFeaturedFeed(@PathVariable Long id) {
        featuredFeedAdminService.deleteFeaturedFeed(id);
        return ApiResult.<Void>builder().build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ApiResult<FeaturedFeedResponse> toggleActive(
            @PathVariable Long id,
            @RequestParam(name = "admin_id", required = false, defaultValue = "admin") String adminId) {
        return ApiResult.<FeaturedFeedResponse>builder()
            .value(featuredFeedAdminService.toggleActive(id, adminId))
            .build();
    }
}
