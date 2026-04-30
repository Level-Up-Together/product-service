package io.pinkspider.leveluptogethermvp.feedservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.feedservice.application.FeedCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 피드 댓글 어드민 internal API.
 * 신고 처리(CONTENT_DELETED) 등 어드민 액션에서 호출.
 */
@RestController
@RequestMapping("/api/internal/feed-comments")
@RequiredArgsConstructor
public class FeedCommentInternalController {

    private final FeedCommandService feedCommandService;

    @DeleteMapping("/{commentId}")
    public ApiResult<Void> deleteCommentByAdmin(
            @PathVariable Long commentId,
            @RequestParam(required = false) String reason) {
        feedCommandService.deleteCommentByAdmin(commentId, reason);
        return ApiResult.<Void>builder().build();
    }
}