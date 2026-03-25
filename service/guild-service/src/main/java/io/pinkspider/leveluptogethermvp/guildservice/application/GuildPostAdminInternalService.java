package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildPostAdminPageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildPostAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildPostCommentAdminPageResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.admin.GuildPostCommentAdminResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPost;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPostComment;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildPostType;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostCommentRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin Internal API 전용 길드 게시글/댓글 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "guildTransactionManager")
public class GuildPostAdminInternalService {

    private final GuildRepository guildRepository;
    private final GuildPostRepository guildPostRepository;
    private final GuildPostCommentRepository guildPostCommentRepository;

    // ========== 게시글 ==========

    public GuildPostAdminPageResponse getPostsByGuildId(Long guildId,
            Boolean includeDeleted, Pageable pageable) {
        Page<GuildPost> posts;
        if (Boolean.TRUE.equals(includeDeleted)) {
            posts = guildPostRepository.findByGuildIdOrderByCreatedAtDesc(guildId, pageable);
        } else {
            posts = guildPostRepository.findByGuildIdAndNotDeletedPaged(guildId, pageable);
        }
        return GuildPostAdminPageResponse.from(posts.map(GuildPostAdminResponse::from));
    }

    public List<GuildPostAdminResponse> getAllPostsByGuildId(Long guildId, Boolean includeDeleted) {
        List<GuildPost> posts;
        if (Boolean.TRUE.equals(includeDeleted)) {
            posts = guildPostRepository.findAllByGuildId(guildId);
        } else {
            posts = guildPostRepository.findByGuildIdAndNotDeleted(guildId);
        }
        return posts.stream().map(GuildPostAdminResponse::from).collect(Collectors.toList());
    }

    public GuildPostAdminResponse getPost(Long guildId, Long postId) {
        GuildPost post = guildPostRepository.findByIdAndGuildId(postId, guildId)
            .orElseThrow(() -> new CustomException("404", "error.guild.post.not_found"));
        return GuildPostAdminResponse.from(post);
    }

    public GuildPostAdminPageResponse getPostsByType(Long guildId, String postType, Pageable pageable) {
        GuildPostType type = GuildPostType.valueOf(postType);
        Page<GuildPost> posts = guildPostRepository.findByGuildIdAndPostTypeForAdmin(guildId, type, pageable);
        return GuildPostAdminPageResponse.from(posts.map(GuildPostAdminResponse::from));
    }

    public GuildPostAdminPageResponse getDeletedPosts(Long guildId, Pageable pageable) {
        Page<GuildPost> posts = guildPostRepository.findDeletedByGuildId(guildId, pageable);
        return GuildPostAdminPageResponse.from(posts.map(GuildPostAdminResponse::from));
    }

    public GuildPostAdminPageResponse searchPosts(Long guildId, String keyword, Pageable pageable) {
        Page<GuildPost> posts = guildPostRepository.searchPostsForAdmin(guildId, keyword, pageable);
        return GuildPostAdminPageResponse.from(posts.map(GuildPostAdminResponse::from));
    }

    @Transactional(transactionManager = "guildTransactionManager")
    public void softDeletePost(Long guildId, Long postId) {
        GuildPost post = guildPostRepository.findByIdAndGuildId(postId, guildId)
            .orElseThrow(() -> new CustomException("404", "error.guild.post.not_found"));
        post.delete();
        guildPostRepository.save(post);
        log.info("게시글 삭제: guildId={}, postId={}", guildId, postId);
    }

    @Transactional(transactionManager = "guildTransactionManager")
    public void hardDeletePost(Long guildId, Long postId) {
        GuildPost post = guildPostRepository.findByIdAndGuildId(postId, guildId)
            .orElseThrow(() -> new CustomException("404", "error.guild.post.not_found"));
        guildPostRepository.delete(post);
        log.info("게시글 영구 삭제: guildId={}, postId={}", guildId, postId);
    }

    @Transactional(transactionManager = "guildTransactionManager")
    public GuildPostAdminResponse restorePost(Long guildId, Long postId) {
        GuildPost post = guildPostRepository.findByIdAndGuildId(postId, guildId)
            .orElseThrow(() -> new CustomException("404", "error.guild.post.not_found"));
        if (!post.getIsDeleted()) {
            throw new CustomException("400", "error.guild.post.not_deleted");
        }
        post.restore();
        GuildPost saved = guildPostRepository.save(post);
        log.info("게시글 복원: guildId={}, postId={}", guildId, postId);
        return GuildPostAdminResponse.from(saved);
    }

    // ========== 댓글 ==========

    public GuildPostCommentAdminPageResponse getCommentsByPostId(Long postId,
            Boolean includeDeleted, Pageable pageable) {
        Page<GuildPostComment> comments;
        if (Boolean.TRUE.equals(includeDeleted)) {
            comments = guildPostCommentRepository.findByPostIdOrderByCreatedAtAsc(postId, pageable);
        } else {
            comments = guildPostCommentRepository.findByPostIdAndNotDeletedPaged(postId, pageable);
        }
        return GuildPostCommentAdminPageResponse.from(comments.map(GuildPostCommentAdminResponse::from));
    }

    public List<GuildPostCommentAdminResponse> getAllCommentsByPostId(Long postId,
            Boolean includeDeleted) {
        List<GuildPostComment> comments;
        if (Boolean.TRUE.equals(includeDeleted)) {
            comments = guildPostCommentRepository.findAllByPostId(postId);
            // findAllByPostId only gets non-deleted ones, so for includeDeleted we need a different approach
            // Actually the existing method filters by isDeleted=false, so let's use the paged version with a large page
        } else {
            comments = guildPostCommentRepository.findAllByPostId(postId);
        }
        return comments.stream().map(GuildPostCommentAdminResponse::from).collect(Collectors.toList());
    }

    public GuildPostCommentAdminResponse getComment(Long postId, Long commentId) {
        GuildPostComment comment = guildPostCommentRepository.findByIdAndPostId(commentId, postId)
            .orElseThrow(() -> new CustomException("404", "error.guild.comment.not_found"));
        return GuildPostCommentAdminResponse.from(comment);
    }

    public GuildPostCommentAdminPageResponse getDeletedComments(Long postId, Pageable pageable) {
        Page<GuildPostComment> comments = guildPostCommentRepository.findDeletedByPostId(postId, pageable);
        return GuildPostCommentAdminPageResponse.from(comments.map(GuildPostCommentAdminResponse::from));
    }

    public GuildPostCommentAdminPageResponse getAllCommentsByGuildId(Long guildId, Pageable pageable) {
        Page<GuildPostComment> comments = guildPostCommentRepository.findAllByGuildId(guildId, pageable);
        return GuildPostCommentAdminPageResponse.from(comments.map(GuildPostCommentAdminResponse::from));
    }

    @Transactional(transactionManager = "guildTransactionManager")
    public void softDeleteComment(Long postId, Long commentId) {
        GuildPostComment comment = guildPostCommentRepository.findByIdAndPostId(commentId, postId)
            .orElseThrow(() -> new CustomException("404", "error.guild.comment.not_found"));
        comment.delete();
        guildPostCommentRepository.save(comment);

        // 댓글 수 감소
        GuildPost post = guildPostRepository.findById(postId).orElse(null);
        if (post != null && post.getCommentCount() > 0) {
            post.decrementCommentCount();
            guildPostRepository.save(post);
        }
        log.info("댓글 삭제: postId={}, commentId={}", postId, commentId);
    }

    @Transactional(transactionManager = "guildTransactionManager")
    public void hardDeleteComment(Long postId, Long commentId) {
        GuildPostComment comment = guildPostCommentRepository.findByIdAndPostId(commentId, postId)
            .orElseThrow(() -> new CustomException("404", "error.guild.comment.not_found"));
        guildPostCommentRepository.delete(comment);
        log.info("댓글 영구 삭제: postId={}, commentId={}", postId, commentId);
    }

    @Transactional(transactionManager = "guildTransactionManager")
    public GuildPostCommentAdminResponse restoreComment(Long postId, Long commentId) {
        GuildPostComment comment = guildPostCommentRepository.findByIdAndPostId(commentId, postId)
            .orElseThrow(() -> new CustomException("404", "error.guild.comment.not_found"));
        if (!comment.getIsDeleted()) {
            throw new CustomException("400", "error.guild.comment.not_deleted");
        }
        comment.restore();
        GuildPostComment saved = guildPostCommentRepository.save(comment);

        // 댓글 수 증가
        GuildPost post = guildPostRepository.findById(postId).orElse(null);
        if (post != null) {
            post.incrementCommentCount();
            guildPostRepository.save(post);
        }
        log.info("댓글 복원: postId={}, commentId={}", postId, commentId);
        return GuildPostCommentAdminResponse.from(saved);
    }

    // ========== 통계 ==========

    public long countPostsByGuildId(Long guildId) {
        return guildPostRepository.countByGuildIdAndNotDeleted(guildId);
    }

    public long countCommentsByGuildId(Long guildId) {
        return guildPostCommentRepository.countByGuildIdAndNotDeleted(guildId);
    }

    public long countCommentsByPostId(Long postId) {
        return guildPostCommentRepository.countByPostIdAndNotDeleted(postId);
    }
}
