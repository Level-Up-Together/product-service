package io.pinkspider.leveluptogethermvp.feedservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.admin.FeedAdminDeleteRequest;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.admin.FeedAdminPageResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.admin.FeedAdminResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.admin.FeedAdminStatsResponse;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedCommandService;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 (Admin Backend → MVP 서비스 간 통신) 인증 불필요 (SecurityConfig에서 /api/internal/**
 * permitAll)
 */
@RestController
@RequestMapping("/api/internal/feeds")
@RequiredArgsConstructor
public class FeedInternalAdminController {

    private final FeedQueryService feedQueryService;
    private final FeedCommandService feedCommandService;

    /** 피드 검색 (페이징 + 필터) */
    @GetMapping
    public ApiResult<FeedAdminPageResponse> searchFeeds(
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "sort_by", required = false, defaultValue = "id") String sortBy,
            @RequestParam(name = "sort_direction", required = false, defaultValue = "DESC")
                    String sortDirection) {

        return ApiResult.<FeedAdminPageResponse>builder()
                .value(
                        feedQueryService.searchFeedsForAdmin(
                                activityType,
                                visibility,
                                userId,
                                categoryId,
                                keyword,
                                page,
                                size,
                                sortBy,
                                sortDirection))
                .build();
    }

    /** 피드 상세 조회 */
    @GetMapping("/{id}")
    public ApiResult<FeedAdminResponse> getFeed(@PathVariable Long id) {
        return ApiResult.<FeedAdminResponse>builder()
                .value(feedQueryService.getFeedForAdmin(id))
                .build();
    }

    /** 피드 삭제 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteFeed(
            @PathVariable Long id, @Valid @RequestBody FeedAdminDeleteRequest request) {
        feedCommandService.deleteFeedByAdmin(id, request.reason(), request.adminInfo());
        return ApiResult.<Void>builder().build();
    }

    /** 피드 통계 */
    @GetMapping("/stats")
    public ApiResult<FeedAdminStatsResponse> getStats() {
        return ApiResult.<FeedAdminStatsResponse>builder()
                .value(feedQueryService.getFeedStats())
                .build();
    }
}
