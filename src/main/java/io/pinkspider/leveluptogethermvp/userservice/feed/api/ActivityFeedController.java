package io.pinkspider.leveluptogethermvp.userservice.feed.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.ActivityFeedResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.CreateFeedRequest;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.FeedCommentRequest;
import io.pinkspider.leveluptogethermvp.userservice.feed.api.dto.FeedCommentResponse;
import io.pinkspider.leveluptogethermvp.userservice.feed.application.ActivityFeedService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    private final ActivityFeedService activityFeedService;

    /**
     * 전체 공개 피드 조회
     */
    @GetMapping("/public")
    public ResponseEntity<ApiResult<Page<ActivityFeedResponse>>> getPublicFeeds(
        @RequestHeader(value = "X-User-Id", required = false) String userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ActivityFeedResponse> feeds = activityFeedService.getPublicFeeds(userId, page, size);
        return ResponseEntity.ok(ApiResult.<Page<ActivityFeedResponse>>builder().value(feeds).build());
    }

    /**
     * 내 타임라인 피드 조회 (내 피드 + 친구 피드)
     */
    @GetMapping("/timeline")
    public ResponseEntity<ApiResult<Page<ActivityFeedResponse>>> getTimelineFeeds(
        @RequestHeader("X-User-Id") String userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ActivityFeedResponse> feeds = activityFeedService.getTimelineFeeds(userId, page, size);
        return ResponseEntity.ok(ApiResult.<Page<ActivityFeedResponse>>builder().value(feeds).build());
    }

    /**
     * 특정 사용자의 피드 조회
     */
    @GetMapping("/user/{targetUserId}")
    public ResponseEntity<ApiResult<Page<ActivityFeedResponse>>> getUserFeeds(
        @PathVariable String targetUserId,
        @RequestHeader("X-User-Id") String currentUserId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ActivityFeedResponse> feeds = activityFeedService.getUserFeeds(targetUserId, currentUserId, page, size);
        return ResponseEntity.ok(ApiResult.<Page<ActivityFeedResponse>>builder().value(feeds).build());
    }

    /**
     * 길드 피드 조회
     */
    @GetMapping("/guild/{guildId}")
    public ResponseEntity<ApiResult<Page<ActivityFeedResponse>>> getGuildFeeds(
        @PathVariable Long guildId,
        @RequestHeader("X-User-Id") String currentUserId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ActivityFeedResponse> feeds = activityFeedService.getGuildFeeds(guildId, currentUserId, page, size);
        return ResponseEntity.ok(ApiResult.<Page<ActivityFeedResponse>>builder().value(feeds).build());
    }

    /**
     * 카테고리별 피드 조회
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResult<Page<ActivityFeedResponse>>> getFeedsByCategory(
        @PathVariable String category,
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ActivityFeedResponse> feeds = activityFeedService.getFeedsByCategory(category, currentUserId, page, size);
        return ResponseEntity.ok(ApiResult.<Page<ActivityFeedResponse>>builder().value(feeds).build());
    }

    /**
     * 피드 상세 조회
     */
    @GetMapping("/{feedId}")
    public ResponseEntity<ApiResult<ActivityFeedResponse>> getFeed(
        @PathVariable Long feedId,
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId
    ) {
        ActivityFeedResponse feed = activityFeedService.getFeed(feedId, currentUserId);
        return ResponseEntity.ok(ApiResult.<ActivityFeedResponse>builder().value(feed).build());
    }

    /**
     * 피드 생성 (사용자 직접 생성)
     */
    @PostMapping
    public ResponseEntity<ApiResult<ActivityFeedResponse>> createFeed(
        @RequestHeader("X-User-Id") String userId,
        @RequestHeader(value = "X-User-Nickname", required = false) String nickname,
        @Valid @RequestBody CreateFeedRequest request
    ) {
        // 닉네임이 없으면 기본값 사용
        String userNickname = nickname != null ? nickname : "사용자";
        ActivityFeedResponse feed = activityFeedService.createFeed(
            userId, userNickname, null, request);
        return ResponseEntity.ok(ApiResult.<ActivityFeedResponse>builder().value(feed).build());
    }

    /**
     * 피드 삭제
     */
    @DeleteMapping("/{feedId}")
    public ResponseEntity<ApiResult<Void>> deleteFeed(
        @PathVariable Long feedId,
        @RequestHeader("X-User-Id") String userId
    ) {
        activityFeedService.deleteFeed(feedId, userId);
        return ResponseEntity.ok(ApiResult.<Void>builder().build());
    }

    /**
     * 좋아요 토글
     */
    @PostMapping("/{feedId}/like")
    public ResponseEntity<ApiResult<Map<String, Boolean>>> toggleLike(
        @PathVariable Long feedId,
        @RequestHeader("X-User-Id") String userId
    ) {
        boolean liked = activityFeedService.toggleLike(feedId, userId);
        return ResponseEntity.ok(ApiResult.<Map<String, Boolean>>builder().value(Map.of("liked", liked)).build());
    }

    /**
     * 댓글 목록 조회
     */
    @GetMapping("/{feedId}/comments")
    public ResponseEntity<ApiResult<Page<FeedCommentResponse>>> getComments(
        @PathVariable Long feedId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<FeedCommentResponse> comments = activityFeedService.getComments(feedId, page, size);
        return ResponseEntity.ok(ApiResult.<Page<FeedCommentResponse>>builder().value(comments).build());
    }

    /**
     * 댓글 작성
     */
    @PostMapping("/{feedId}/comments")
    public ResponseEntity<ApiResult<FeedCommentResponse>> addComment(
        @PathVariable Long feedId,
        @RequestHeader("X-User-Id") String userId,
        @RequestHeader(value = "X-User-Nickname", required = false) String nickname,
        @Valid @RequestBody FeedCommentRequest request
    ) {
        // 닉네임이 없으면 기본값 사용
        String userNickname = nickname != null ? nickname : "사용자";
        FeedCommentResponse comment = activityFeedService.addComment(
            feedId, userId, userNickname, request);
        return ResponseEntity.ok(ApiResult.<FeedCommentResponse>builder().value(comment).build());
    }

    /**
     * 댓글 삭제
     */
    @DeleteMapping("/{feedId}/comments/{commentId}")
    public ResponseEntity<ApiResult<Void>> deleteComment(
        @PathVariable Long feedId,
        @PathVariable Long commentId,
        @RequestHeader("X-User-Id") String userId
    ) {
        activityFeedService.deleteComment(feedId, commentId, userId);
        return ResponseEntity.ok(ApiResult.<Void>builder().build());
    }
}
