package io.pinkspider.leveluptogethermvp.guildservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildPostAdminInternalService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildPostAdminPageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildPostAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildPostCommentAdminPageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildPostCommentAdminResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 내부 API 컨트롤러 - GuildPost / GuildPostComment 인증 불필요 (SecurityConfig에서 /api/internal/**
 * permitAll)
 */
@RestController
@RequestMapping("/api/internal/guilds/{guildId}")
@RequiredArgsConstructor
public class GuildPostAdminInternalController {

    private final GuildPostAdminInternalService guildPostAdminInternalService;

    // ========== 게시글 API ==========

    @GetMapping("/posts")
    public ApiResult<GuildPostAdminPageResponse> getPostsByGuildId(
            @PathVariable Long guildId,
            @RequestParam(name = "include_deleted", required = false, defaultValue = "false")
                    Boolean includeDeleted,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResult.<GuildPostAdminPageResponse>builder()
                .value(
                        guildPostAdminInternalService.getPostsByGuildId(
                                guildId, includeDeleted, pageable))
                .build();
    }

    @GetMapping("/posts/all")
    public ApiResult<List<GuildPostAdminResponse>> getAllPostsByGuildId(
            @PathVariable Long guildId,
            @RequestParam(name = "include_deleted", required = false, defaultValue = "false")
                    Boolean includeDeleted) {
        return ApiResult.<List<GuildPostAdminResponse>>builder()
                .value(guildPostAdminInternalService.getAllPostsByGuildId(guildId, includeDeleted))
                .build();
    }

    @GetMapping("/posts/{postId}")
    public ApiResult<GuildPostAdminResponse> getPost(
            @PathVariable Long guildId, @PathVariable Long postId) {
        return ApiResult.<GuildPostAdminResponse>builder()
                .value(guildPostAdminInternalService.getPost(guildId, postId))
                .build();
    }

    @GetMapping("/posts/type/{postType}")
    public ApiResult<GuildPostAdminPageResponse> getPostsByType(
            @PathVariable Long guildId,
            @PathVariable String postType,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResult.<GuildPostAdminPageResponse>builder()
                .value(guildPostAdminInternalService.getPostsByType(guildId, postType, pageable))
                .build();
    }

    @GetMapping("/posts/deleted")
    public ApiResult<GuildPostAdminPageResponse> getDeletedPosts(
            @PathVariable Long guildId, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResult.<GuildPostAdminPageResponse>builder()
                .value(guildPostAdminInternalService.getDeletedPosts(guildId, pageable))
                .build();
    }

    @GetMapping("/posts/search")
    public ApiResult<GuildPostAdminPageResponse> searchPosts(
            @PathVariable Long guildId,
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResult.<GuildPostAdminPageResponse>builder()
                .value(guildPostAdminInternalService.searchPosts(guildId, keyword, pageable))
                .build();
    }

    @DeleteMapping("/posts/{postId}")
    public ApiResult<Void> softDeletePost(@PathVariable Long guildId, @PathVariable Long postId) {
        guildPostAdminInternalService.softDeletePost(guildId, postId);
        return ApiResult.<Void>builder().build();
    }

    @DeleteMapping("/posts/{postId}/hard")
    public ApiResult<Void> hardDeletePost(@PathVariable Long guildId, @PathVariable Long postId) {
        guildPostAdminInternalService.hardDeletePost(guildId, postId);
        return ApiResult.<Void>builder().build();
    }

    @PatchMapping("/posts/{postId}/restore")
    public ApiResult<GuildPostAdminResponse> restorePost(
            @PathVariable Long guildId, @PathVariable Long postId) {
        return ApiResult.<GuildPostAdminResponse>builder()
                .value(guildPostAdminInternalService.restorePost(guildId, postId))
                .build();
    }

    @GetMapping("/posts/count")
    public ApiResult<Long> countPostsByGuildId(@PathVariable Long guildId) {
        return ApiResult.<Long>builder()
                .value(guildPostAdminInternalService.countPostsByGuildId(guildId))
                .build();
    }

    // ========== 댓글 API ==========

    @GetMapping("/posts/{postId}/comments")
    public ApiResult<GuildPostCommentAdminPageResponse> getCommentsByPostId(
            @PathVariable Long guildId,
            @PathVariable Long postId,
            @RequestParam(name = "include_deleted", required = false, defaultValue = "false")
                    Boolean includeDeleted,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiResult.<GuildPostCommentAdminPageResponse>builder()
                .value(
                        guildPostAdminInternalService.getCommentsByPostId(
                                postId, includeDeleted, pageable))
                .build();
    }

    @GetMapping("/posts/{postId}/comments/all")
    public ApiResult<List<GuildPostCommentAdminResponse>> getAllCommentsByPostId(
            @PathVariable Long guildId,
            @PathVariable Long postId,
            @RequestParam(name = "include_deleted", required = false, defaultValue = "false")
                    Boolean includeDeleted) {
        return ApiResult.<List<GuildPostCommentAdminResponse>>builder()
                .value(guildPostAdminInternalService.getAllCommentsByPostId(postId, includeDeleted))
                .build();
    }

    @GetMapping("/posts/{postId}/comments/{commentId}")
    public ApiResult<GuildPostCommentAdminResponse> getComment(
            @PathVariable Long guildId, @PathVariable Long postId, @PathVariable Long commentId) {
        return ApiResult.<GuildPostCommentAdminResponse>builder()
                .value(guildPostAdminInternalService.getComment(postId, commentId))
                .build();
    }

    @GetMapping("/posts/{postId}/comments/deleted")
    public ApiResult<GuildPostCommentAdminPageResponse> getDeletedComments(
            @PathVariable Long guildId,
            @PathVariable Long postId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiResult.<GuildPostCommentAdminPageResponse>builder()
                .value(guildPostAdminInternalService.getDeletedComments(postId, pageable))
                .build();
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ApiResult<Void> softDeleteComment(
            @PathVariable Long guildId, @PathVariable Long postId, @PathVariable Long commentId) {
        guildPostAdminInternalService.softDeleteComment(postId, commentId);
        return ApiResult.<Void>builder().build();
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}/hard")
    public ApiResult<Void> hardDeleteComment(
            @PathVariable Long guildId, @PathVariable Long postId, @PathVariable Long commentId) {
        guildPostAdminInternalService.hardDeleteComment(postId, commentId);
        return ApiResult.<Void>builder().build();
    }

    @PatchMapping("/posts/{postId}/comments/{commentId}/restore")
    public ApiResult<GuildPostCommentAdminResponse> restoreComment(
            @PathVariable Long guildId, @PathVariable Long postId, @PathVariable Long commentId) {
        return ApiResult.<GuildPostCommentAdminResponse>builder()
                .value(guildPostAdminInternalService.restoreComment(postId, commentId))
                .build();
    }

    @GetMapping("/posts/{postId}/comments/count")
    public ApiResult<Long> countCommentsByPostId(
            @PathVariable Long guildId, @PathVariable Long postId) {
        return ApiResult.<Long>builder()
                .value(guildPostAdminInternalService.countCommentsByPostId(postId))
                .build();
    }

    // ========== 길드 전체 댓글 API ==========

    @GetMapping("/comments")
    public ApiResult<GuildPostCommentAdminPageResponse> getAllCommentsByGuildId(
            @PathVariable Long guildId, @PageableDefault(size = 50) Pageable pageable) {
        return ApiResult.<GuildPostCommentAdminPageResponse>builder()
                .value(guildPostAdminInternalService.getAllCommentsByGuildId(guildId, pageable))
                .build();
    }

    @GetMapping("/comments/count")
    public ApiResult<Long> countCommentsByGuildId(@PathVariable Long guildId) {
        return ApiResult.<Long>builder()
                .value(guildPostAdminInternalService.countCommentsByGuildId(guildId))
                .build();
    }
}
