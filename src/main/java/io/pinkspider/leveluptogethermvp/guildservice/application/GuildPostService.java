package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCommentUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostCreateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostListResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildPostUpdateRequest;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPost;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildPostComment;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildPostType;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostCommentRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildPostRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.global.event.GuildBulletinCreatedEvent;
import io.pinkspider.leveluptogethermvp.supportservice.report.api.dto.ReportTargetType;
import io.pinkspider.leveluptogethermvp.supportservice.report.application.ReportService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "guildTransactionManager", readOnly = true)
public class GuildPostService {

    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final GuildPostRepository guildPostRepository;
    private final GuildPostCommentRepository guildPostCommentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReportService reportService;

    /**
     * 게시글 작성
     * - 공지글(NOTICE)은 길드 마스터 또는 부길드마스터만 작성 가능
     * - 일반글(NORMAL)은 모든 길드원 작성 가능
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildPostResponse createPost(Long guildId, String userId, String userNickname, GuildPostCreateRequest request) {
        Guild guild = findActiveGuild(guildId);
        GuildMember member = validateMembership(guildId, userId);

        // 공지글은 마스터 또는 부길드마스터만 작성 가능
        if (request.getPostType() == GuildPostType.NOTICE && !member.isMasterOrSubMaster()) {
            throw new IllegalStateException("공지글은 길드 마스터 또는 부길드마스터만 작성할 수 있습니다.");
        }

        // 상단 고정은 마스터 또는 부길드마스터만 가능
        boolean isPinned = Boolean.TRUE.equals(request.getIsPinned()) && member.isMasterOrSubMaster();

        GuildPost post = GuildPost.builder()
            .guild(guild)
            .authorId(userId)
            .authorNickname(userNickname)
            .title(request.getTitle())
            .content(request.getContent())
            .postType(request.getPostType())
            .isPinned(isPinned)
            .build();

        GuildPost savedPost = guildPostRepository.save(post);
        log.info("길드 게시글 작성: guildId={}, postId={}, type={}, author={}",
            guildId, savedPost.getId(), request.getPostType(), userId);

        // 공지글인 경우 길드원들에게 알림 발송
        if (request.getPostType() == GuildPostType.NOTICE) {
            publishBulletinCreatedEvent(guild, savedPost, userId);
        }

        return GuildPostResponse.from(savedPost);
    }

    /**
     * 게시글 목록 조회 (상단 고정 우선, 최신순)
     */
    public Page<GuildPostListResponse> getPosts(Long guildId, String userId, Pageable pageable) {
        findActiveGuild(guildId);
        validateMembership(guildId, userId);

        Page<GuildPost> posts = guildPostRepository.findByGuildIdOrderByPinnedAndCreatedAt(guildId, pageable);

        // 배치로 신고 상태 조회
        List<String> postIds = posts.getContent().stream()
            .map(p -> String.valueOf(p.getId()))
            .toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.GUILD_NOTICE, postIds);

        return posts.map(post -> {
            GuildPostListResponse response = GuildPostListResponse.from(post);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(post.getId()), false));
            return response;
        });
    }

    /**
     * 게시글 유형별 조회
     */
    public Page<GuildPostListResponse> getPostsByType(Long guildId, String userId, GuildPostType postType, Pageable pageable) {
        findActiveGuild(guildId);
        validateMembership(guildId, userId);

        Page<GuildPost> posts = guildPostRepository.findByGuildIdAndPostType(guildId, postType, pageable);

        // 배치로 신고 상태 조회
        List<String> postIds = posts.getContent().stream()
            .map(p -> String.valueOf(p.getId()))
            .toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.GUILD_NOTICE, postIds);

        return posts.map(post -> {
            GuildPostListResponse response = GuildPostListResponse.from(post);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(post.getId()), false));
            return response;
        });
    }

    /**
     * 공지글 목록 조회
     */
    public List<GuildPostListResponse> getNotices(Long guildId, String userId) {
        findActiveGuild(guildId);
        validateMembership(guildId, userId);

        List<GuildPost> posts = guildPostRepository.findNotices(guildId);
        List<GuildPostListResponse> result = posts.stream()
            .map(GuildPostListResponse::from)
            .collect(Collectors.toList());

        // 배치로 신고 상태 조회
        if (!result.isEmpty()) {
            List<String> postIds = result.stream()
                .map(r -> String.valueOf(r.getId()))
                .toList();
            Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.GUILD_NOTICE, postIds);
            result.forEach(r -> r.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(r.getId()), false)));
        }

        return result;
    }

    /**
     * 게시글 검색
     */
    public Page<GuildPostListResponse> searchPosts(Long guildId, String userId, String keyword, Pageable pageable) {
        findActiveGuild(guildId);
        validateMembership(guildId, userId);

        Page<GuildPost> posts = guildPostRepository.searchPosts(guildId, keyword, pageable);

        // 배치로 신고 상태 조회
        List<String> postIds = posts.getContent().stream()
            .map(p -> String.valueOf(p.getId()))
            .toList();
        Map<String, Boolean> underReviewMap = reportService.isUnderReviewBatch(ReportTargetType.GUILD_NOTICE, postIds);

        return posts.map(post -> {
            GuildPostListResponse response = GuildPostListResponse.from(post);
            response.setIsUnderReview(underReviewMap.getOrDefault(String.valueOf(post.getId()), false));
            return response;
        });
    }

    /**
     * 게시글 상세 조회 (조회수 증가)
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildPostResponse getPost(Long guildId, Long postId, String userId) {
        findActiveGuild(guildId);
        validateMembership(guildId, userId);

        GuildPost post = findActivePost(postId);
        validatePostBelongsToGuild(post, guildId);

        post.incrementViewCount();

        GuildPostResponse response = GuildPostResponse.from(post);
        // 신고 처리중 여부 확인
        response.setIsUnderReview(reportService.isUnderReview(ReportTargetType.GUILD_NOTICE, String.valueOf(postId)));

        return response;
    }

    /**
     * 게시글 수정 (작성자만 가능)
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildPostResponse updatePost(Long guildId, Long postId, String userId, GuildPostUpdateRequest request) {
        findActiveGuild(guildId);
        validateMembership(guildId, userId);

        GuildPost post = findActivePost(postId);
        validatePostBelongsToGuild(post, guildId);

        if (!post.isAuthor(userId)) {
            throw new IllegalStateException("게시글 작성자만 수정할 수 있습니다.");
        }

        post.update(request.getTitle(), request.getContent());
        log.info("길드 게시글 수정: postId={}, author={}", postId, userId);

        return GuildPostResponse.from(post);
    }

    /**
     * 게시글 삭제 (작성자 또는 마스터/부길드마스터 가능)
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public void deletePost(Long guildId, Long postId, String userId) {
        findActiveGuild(guildId);
        GuildMember member = validateMembership(guildId, userId);

        GuildPost post = findActivePost(postId);
        validatePostBelongsToGuild(post, guildId);

        // 작성자이거나 마스터/부길드마스터인 경우에만 삭제 가능
        if (!post.isAuthor(userId) && !member.isMasterOrSubMaster()) {
            throw new IllegalStateException("게시글을 삭제할 권한이 없습니다.");
        }

        post.delete();
        log.info("길드 게시글 삭제: postId={}, deletedBy={}", postId, userId);
    }

    /**
     * 게시글 상단 고정/해제 (마스터 또는 부길드마스터 가능)
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildPostResponse togglePin(Long guildId, Long postId, String userId) {
        findActiveGuild(guildId);
        GuildMember member = validateMembership(guildId, userId);

        if (!member.isMasterOrSubMaster()) {
            throw new IllegalStateException("게시글 상단 고정은 길드 마스터 또는 부길드마스터만 할 수 있습니다.");
        }

        GuildPost post = findActivePost(postId);
        validatePostBelongsToGuild(post, guildId);

        if (post.getIsPinned()) {
            post.unpin();
        } else {
            post.pin();
        }

        log.info("길드 게시글 고정 토글: postId={}, isPinned={}", postId, post.getIsPinned());

        return GuildPostResponse.from(post);
    }

    // =====================================================
    // 댓글 관련 메서드
    // =====================================================

    /**
     * 댓글 작성 (길드원만 가능)
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildPostCommentResponse createComment(Long guildId, Long postId, String userId, String userNickname,
                                                   GuildPostCommentCreateRequest request) {
        findActiveGuild(guildId);
        validateMembership(guildId, userId);

        GuildPost post = findActivePost(postId);
        validatePostBelongsToGuild(post, guildId);

        GuildPostComment parentComment = null;
        if (request.getParentId() != null) {
            parentComment = guildPostCommentRepository.findByIdAndIsDeletedFalse(request.getParentId())
                .orElseThrow(() -> new IllegalArgumentException("상위 댓글을 찾을 수 없습니다."));

            if (!parentComment.getPost().getId().equals(postId)) {
                throw new IllegalArgumentException("상위 댓글이 해당 게시글에 속하지 않습니다.");
            }
        }

        GuildPostComment comment = GuildPostComment.builder()
            .post(post)
            .authorId(userId)
            .authorNickname(userNickname)
            .content(request.getContent())
            .parent(parentComment)
            .build();

        GuildPostComment savedComment = guildPostCommentRepository.save(comment);
        post.incrementCommentCount();

        log.info("길드 게시글 댓글 작성: postId={}, commentId={}, author={}",
            postId, savedComment.getId(), userId);

        return GuildPostCommentResponse.from(savedComment);
    }

    /**
     * 댓글 목록 조회 (대댓글 포함)
     */
    public List<GuildPostCommentResponse> getComments(Long guildId, Long postId, String userId) {
        findActiveGuild(guildId);
        validateMembership(guildId, userId);

        GuildPost post = findActivePost(postId);
        validatePostBelongsToGuild(post, guildId);

        List<GuildPostComment> rootComments = guildPostCommentRepository.findAllByPostId(postId).stream()
            .filter(c -> c.getParent() == null)
            .toList();

        return rootComments.stream()
            .map(comment -> {
                List<GuildPostCommentResponse> replies = guildPostCommentRepository.findRepliesByParentId(comment.getId())
                    .stream()
                    .map(GuildPostCommentResponse::from)
                    .collect(Collectors.toList());
                return GuildPostCommentResponse.fromWithReplies(comment, replies);
            })
            .collect(Collectors.toList());
    }

    /**
     * 댓글 수정 (작성자만 가능)
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildPostCommentResponse updateComment(Long guildId, Long postId, Long commentId, String userId,
                                                   GuildPostCommentUpdateRequest request) {
        findActiveGuild(guildId);
        validateMembership(guildId, userId);

        GuildPost post = findActivePost(postId);
        validatePostBelongsToGuild(post, guildId);

        GuildPostComment comment = guildPostCommentRepository.findByIdAndIsDeletedFalse(commentId)
            .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

        if (!comment.getPost().getId().equals(postId)) {
            throw new IllegalArgumentException("댓글이 해당 게시글에 속하지 않습니다.");
        }

        if (!comment.isAuthor(userId)) {
            throw new IllegalStateException("댓글 작성자만 수정할 수 있습니다.");
        }

        comment.update(request.getContent());
        log.info("길드 게시글 댓글 수정: commentId={}, author={}", commentId, userId);

        return GuildPostCommentResponse.from(comment);
    }

    /**
     * 댓글 삭제 (작성자 또는 마스터/부길드마스터 가능)
     */
    @Transactional(transactionManager = "guildTransactionManager")
    public void deleteComment(Long guildId, Long postId, Long commentId, String userId) {
        findActiveGuild(guildId);
        GuildMember member = validateMembership(guildId, userId);

        GuildPost post = findActivePost(postId);
        validatePostBelongsToGuild(post, guildId);

        GuildPostComment comment = guildPostCommentRepository.findByIdAndIsDeletedFalse(commentId)
            .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));

        if (!comment.getPost().getId().equals(postId)) {
            throw new IllegalArgumentException("댓글이 해당 게시글에 속하지 않습니다.");
        }

        // 작성자이거나 마스터/부길드마스터인 경우에만 삭제 가능
        if (!comment.isAuthor(userId) && !member.isMasterOrSubMaster()) {
            throw new IllegalStateException("댓글을 삭제할 권한이 없습니다.");
        }

        comment.delete();
        post.decrementCommentCount();

        log.info("길드 게시글 댓글 삭제: commentId={}, deletedBy={}", commentId, userId);
    }

    // =====================================================
    // Helper 메서드
    // =====================================================

    private Guild findActiveGuild(Long guildId) {
        return guildRepository.findByIdAndIsActiveTrue(guildId)
            .orElseThrow(() -> new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId));
    }

    private GuildMember validateMembership(Long guildId, String userId) {
        return guildMemberRepository.findByGuildIdAndUserId(guildId, userId)
            .filter(GuildMember::isActive)
            .orElseThrow(() -> new IllegalStateException("길드원만 접근할 수 있습니다."));
    }

    private GuildPost findActivePost(Long postId) {
        return guildPostRepository.findByIdAndIsDeletedFalse(postId)
            .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다: " + postId));
    }

    private void validatePostBelongsToGuild(GuildPost post, Long guildId) {
        if (!post.getGuild().getId().equals(guildId)) {
            throw new IllegalArgumentException("게시글이 해당 길드에 속하지 않습니다.");
        }
    }

    /**
     * 길드 공지사항 등록 이벤트 발행
     */
    private void publishBulletinCreatedEvent(Guild guild, GuildPost post, String authorId) {
        List<String> memberIds = guildMemberRepository.findActiveMembers(guild.getId()).stream()
            .map(GuildMember::getUserId)
            .filter(memberId -> !memberId.equals(authorId))  // 작성자 제외
            .toList();

        if (!memberIds.isEmpty()) {
            eventPublisher.publishEvent(new GuildBulletinCreatedEvent(
                authorId,
                memberIds,
                guild.getId(),
                guild.getName(),
                post.getId(),
                post.getTitle()
            ));
            log.debug("길드 공지사항 이벤트 발행: guildId={}, postId={}, memberCount={}",
                guild.getId(), post.getId(), memberIds.size());
        }
    }
}
