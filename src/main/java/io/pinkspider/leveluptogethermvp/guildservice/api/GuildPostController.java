package io.pinkspider.leveluptogethermvp.guildservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildPostService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostListResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildPostType;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guilds/{guildId}/posts")
@RequiredArgsConstructor
public class GuildPostController {

    private final GuildPostService guildPostService;

    // =====================================================
    // 게시글 API
    // =====================================================

    /**
     * 게시글 작성
     * - 공지글은 길드 마스터만 작성 가능
     */
    @PostMapping
    public ResponseEntity<ApiResult<GuildPostResponse>> createPost(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @RequestHeader(value = "X-User-Nickname", required = false) String userNickname,
        @Valid @RequestBody GuildPostCreateRequest request) {

        GuildPostResponse response = guildPostService.createPost(guildId, userId, userNickname, request);
        return ResponseEntity.ok(ApiResult.<GuildPostResponse>builder().value(response).build());
    }

    /**
     * 게시글 목록 조회 (상단 고정 우선, 최신순)
     */
    @GetMapping
    public ResponseEntity<ApiResult<Page<GuildPostListResponse>>> getPosts(
        @PathVariable Long guildId,
        @CurrentUser String userId,
        @PageableDefault(size = 20) Pageable pageable) {

        Page<GuildPostListResponse> responses = guildPostService.getPosts(guildId, userId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<GuildPostListResponse>>builder().value(responses).build());
    }

    /**
     * 게시글 유형별 조회 (NOTICE, NORMAL)
     */
    @GetMapping("/type/{postType}")
    public ResponseEntity<ApiResult<Page<GuildPostListResponse>>> getPostsByType(
        @PathVariable Long guildId,
        @PathVariable GuildPostType postType,
        @CurrentUser String userId,
        @PageableDefault(size = 20) Pageable pageable) {

        Page<GuildPostListResponse> responses = guildPostService.getPostsByType(guildId, userId, postType, pageable);
        return ResponseEntity.ok(ApiResult.<Page<GuildPostListResponse>>builder().value(responses).build());
    }

    /**
     * 공지글 목록 조회
     */
    @GetMapping("/notices")
    public ResponseEntity<ApiResult<List<GuildPostListResponse>>> getNotices(
        @PathVariable Long guildId,
        @CurrentUser String userId) {

        List<GuildPostListResponse> responses = guildPostService.getNotices(guildId, userId);
        return ResponseEntity.ok(ApiResult.<List<GuildPostListResponse>>builder().value(responses).build());
    }

    /**
     * 게시글 검색
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResult<Page<GuildPostListResponse>>> searchPosts(
        @PathVariable Long guildId,
        @RequestParam String keyword,
        @CurrentUser String userId,
        @PageableDefault(size = 20) Pageable pageable) {

        Page<GuildPostListResponse> responses = guildPostService.searchPosts(guildId, userId, keyword, pageable);
        return ResponseEntity.ok(ApiResult.<Page<GuildPostListResponse>>builder().value(responses).build());
    }

    /**
     * 게시글 상세 조회
     */
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResult<GuildPostResponse>> getPost(
        @PathVariable Long guildId,
        @PathVariable Long postId,
        @CurrentUser String userId) {

        GuildPostResponse response = guildPostService.getPost(guildId, postId, userId);
        return ResponseEntity.ok(ApiResult.<GuildPostResponse>builder().value(response).build());
    }

    /**
     * 게시글 수정 (작성자만 가능)
     */
    @PutMapping("/{postId}")
    public ResponseEntity<ApiResult<GuildPostResponse>> updatePost(
        @PathVariable Long guildId,
        @PathVariable Long postId,
        @CurrentUser String userId,
        @Valid @RequestBody GuildPostUpdateRequest request) {

        GuildPostResponse response = guildPostService.updatePost(guildId, postId, userId, request);
        return ResponseEntity.ok(ApiResult.<GuildPostResponse>builder().value(response).build());
    }

    /**
     * 게시글 삭제 (작성자 또는 마스터 가능)
     */
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResult<Void>> deletePost(
        @PathVariable Long guildId,
        @PathVariable Long postId,
        @CurrentUser String userId) {

        guildPostService.deletePost(guildId, postId, userId);
        return ResponseEntity.ok(ApiResult.getBase());
    }

    /**
     * 게시글 상단 고정/해제 토글 (마스터만 가능)
     */
    @PatchMapping("/{postId}/pin")
    public ResponseEntity<ApiResult<GuildPostResponse>> togglePin(
        @PathVariable Long guildId,
        @PathVariable Long postId,
        @CurrentUser String userId) {

        GuildPostResponse response = guildPostService.togglePin(guildId, postId, userId);
        return ResponseEntity.ok(ApiResult.<GuildPostResponse>builder().value(response).build());
    }

    // =====================================================
    // 댓글 API
    // =====================================================

    /**
     * 댓글 작성 (길드원만 가능)
     */
    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResult<GuildPostCommentResponse>> createComment(
        @PathVariable Long guildId,
        @PathVariable Long postId,
        @CurrentUser String userId,
        @RequestHeader(value = "X-User-Nickname", required = false) String userNickname,
        @Valid @RequestBody GuildPostCommentCreateRequest request) {

        GuildPostCommentResponse response = guildPostService.createComment(guildId, postId, userId, userNickname, request);
        return ResponseEntity.ok(ApiResult.<GuildPostCommentResponse>builder().value(response).build());
    }

    /**
     * 댓글 목록 조회 (대댓글 포함)
     */
    @GetMapping("/{postId}/comments")
    public ResponseEntity<ApiResult<List<GuildPostCommentResponse>>> getComments(
        @PathVariable Long guildId,
        @PathVariable Long postId,
        @CurrentUser String userId) {

        List<GuildPostCommentResponse> responses = guildPostService.getComments(guildId, postId, userId);
        return ResponseEntity.ok(ApiResult.<List<GuildPostCommentResponse>>builder().value(responses).build());
    }

    /**
     * 댓글 수정 (작성자만 가능)
     */
    @PutMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<ApiResult<GuildPostCommentResponse>> updateComment(
        @PathVariable Long guildId,
        @PathVariable Long postId,
        @PathVariable Long commentId,
        @CurrentUser String userId,
        @Valid @RequestBody GuildPostCommentUpdateRequest request) {

        GuildPostCommentResponse response = guildPostService.updateComment(guildId, postId, commentId, userId, request);
        return ResponseEntity.ok(ApiResult.<GuildPostCommentResponse>builder().value(response).build());
    }

    /**
     * 댓글 삭제 (작성자 또는 마스터 가능)
     */
    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<ApiResult<Void>> deleteComment(
        @PathVariable Long guildId,
        @PathVariable Long postId,
        @PathVariable Long commentId,
        @CurrentUser String userId) {

        guildPostService.deleteComment(guildId, postId, commentId, userId);
        return ResponseEntity.ok(ApiResult.getBase());
    }
}
