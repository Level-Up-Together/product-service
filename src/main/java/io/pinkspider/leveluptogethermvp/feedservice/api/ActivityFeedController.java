package io.pinkspider.leveluptogethermvp.feedservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.CreateFeedRequest;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentRequest;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedCommentResponse;
import io.pinkspider.leveluptogethermvp.feedservice.api.dto.FeedLikeResponse;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedCommandService;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedQueryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feeds")
@RequiredArgsConstructor
public class ActivityFeedController {

    private final FeedQueryService feedQueryService;
    private final FeedCommandService feedCommandService;

    /**
     * 전체 공개 피드 조회 - categoryId가 있으면 해당 카테고리별 피드 조회
     */
    @GetMapping("/public")
    public ResponseEntity<ApiResult<Page<ActivityFeedResponse>>> getPublicFeeds(
        @CurrentUser(required = false) String userId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        Page<ActivityFeedResponse> feeds;
        if (categoryId != null) {
            feeds = feedQueryService.getPublicFeedsByCategory(categoryId, userId, page, size, acceptLanguage);
        } else {
            feeds = feedQueryService.getPublicFeeds(userId, page, size, acceptLanguage);
        }
        return ResponseEntity.ok(ApiResult.<Page<ActivityFeedResponse>>builder().value(feeds).build());
    }

    /**
     * 내 타임라인 피드 조회 (내 피드 + 친구 피드)
     */
    @GetMapping("/timeline")
    public ResponseEntity<ApiResult<Page<ActivityFeedResponse>>> getTimelineFeeds(
        @CurrentUser String userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        Page<ActivityFeedResponse> feeds = feedQueryService.getTimelineFeeds(userId, page, size, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<Page<ActivityFeedResponse>>builder().value(feeds).build());
    }

    /**
     * 특정 사용자의 피드 조회
     */
    @GetMapping("/user/{targetUserId}")
    public ResponseEntity<ApiResult<Page<ActivityFeedResponse>>> getUserFeeds(
        @PathVariable String targetUserId,
        @CurrentUser String currentUserId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        Page<ActivityFeedResponse> feeds = feedQueryService.getUserFeeds(targetUserId, currentUserId, page, size, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<Page<ActivityFeedResponse>>builder().value(feeds).build());
    }

    /**
     * 길드 피드 조회
     */
    @GetMapping("/guild/{guildId}")
    public ResponseEntity<ApiResult<Page<ActivityFeedResponse>>> getGuildFeeds(
        @PathVariable Long guildId,
        @CurrentUser String currentUserId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        Page<ActivityFeedResponse> feeds = feedQueryService.getGuildFeeds(guildId, currentUserId, page, size, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<Page<ActivityFeedResponse>>builder().value(feeds).build());
    }

    /**
     * 카테고리별 피드 조회
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResult<Page<ActivityFeedResponse>>> getFeedsByCategory(
        @PathVariable String category,
        @CurrentUser(required = false) String currentUserId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        Page<ActivityFeedResponse> feeds = feedQueryService.getFeedsByCategory(category, currentUserId, page, size, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<Page<ActivityFeedResponse>>builder().value(feeds).build());
    }

    /**
     * 피드 검색 (미션명/제목 기준) - 검색어는 2글자 이상 필요 - category가 없으면 전체 카테고리에서 검색 - category가 있으면 해당 카테고리 내에서 검색
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResult<Page<ActivityFeedResponse>>> searchFeeds(
        @RequestParam String keyword,
        @RequestParam(required = false) String category,
        @CurrentUser(required = false) String currentUserId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        // 검색어 2글자 이상 검증
        if (keyword == null || keyword.trim().length() < 2) {
            return ResponseEntity.ok(ApiResult.<Page<ActivityFeedResponse>>builder()
                .value(new PageImpl<>(List.of(), PageRequest.of(page, size), 0))
                .build());
        }

        Page<ActivityFeedResponse> feeds;
        if (category != null && !category.isEmpty() && !category.equals("전체")) {
            feeds = feedQueryService.searchFeedsByCategory(keyword.trim(), category, currentUserId, page, size, acceptLanguage);
        } else {
            feeds = feedQueryService.searchFeeds(keyword.trim(), currentUserId, page, size, acceptLanguage);
        }
        return ResponseEntity.ok(ApiResult.<Page<ActivityFeedResponse>>builder().value(feeds).build());
    }

    /**
     * 피드 상세 조회
     */
    @GetMapping("/{feedId}")
    public ResponseEntity<ApiResult<ActivityFeedResponse>> getFeed(
        @PathVariable Long feedId,
        @CurrentUser(required = false) String currentUserId,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        ActivityFeedResponse feed = feedQueryService.getFeed(feedId, currentUserId, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<ActivityFeedResponse>builder().value(feed).build());
    }

    /**
     * 피드 생성 (사용자 직접 생성)
     */
    @PostMapping
    public ResponseEntity<ApiResult<ActivityFeedResponse>> createFeed(
        @CurrentUser String userId,
        @Valid @RequestBody CreateFeedRequest request
    ) {
        ActivityFeedResponse feed = feedCommandService.createFeed(userId, request);
        return ResponseEntity.ok(ApiResult.<ActivityFeedResponse>builder().value(feed).build());
    }

    /**
     * 피드 삭제
     */
    @DeleteMapping("/{feedId}")
    public ResponseEntity<ApiResult<Void>> deleteFeed(
        @PathVariable Long feedId,
        @CurrentUser String userId
    ) {
        feedCommandService.deleteFeed(feedId, userId);
        return ResponseEntity.ok(ApiResult.<Void>builder().build());
    }

    /**
     * 좋아요 토글
     */
    @PostMapping("/{feedId}/like")
    public ResponseEntity<ApiResult<FeedLikeResponse>> toggleLike(
        @PathVariable Long feedId,
        @CurrentUser String userId
    ) {
        FeedLikeResponse response = feedCommandService.toggleLike(feedId, userId);
        return ResponseEntity.ok(ApiResult.<FeedLikeResponse>builder().value(response).build());
    }

    /**
     * 댓글 목록 조회
     */
    @GetMapping("/{feedId}/comments")
    public ResponseEntity<ApiResult<Page<FeedCommentResponse>>> getComments(
        @PathVariable Long feedId,
        @CurrentUser(required = false) String currentUserId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        Page<FeedCommentResponse> comments = feedQueryService.getComments(feedId, currentUserId, page, size, acceptLanguage);
        return ResponseEntity.ok(ApiResult.<Page<FeedCommentResponse>>builder().value(comments).build());
    }

    /**
     * 댓글 작성
     */
    @PostMapping("/{feedId}/comments")
    public ResponseEntity<ApiResult<FeedCommentResponse>> addComment(
        @PathVariable Long feedId,
        @CurrentUser String userId,
        @Valid @RequestBody FeedCommentRequest request
    ) {
        FeedCommentResponse comment = feedCommandService.addComment(feedId, userId, request);
        return ResponseEntity.ok(ApiResult.<FeedCommentResponse>builder().value(comment).build());
    }

    /**
     * 댓글 삭제
     */
    @DeleteMapping("/{feedId}/comments/{commentId}")
    public ResponseEntity<ApiResult<Void>> deleteComment(
        @PathVariable Long feedId,
        @PathVariable Long commentId,
        @CurrentUser String userId
    ) {
        feedCommandService.deleteComment(feedId, commentId, userId);
        return ResponseEntity.ok(ApiResult.<Void>builder().build());
    }
}
