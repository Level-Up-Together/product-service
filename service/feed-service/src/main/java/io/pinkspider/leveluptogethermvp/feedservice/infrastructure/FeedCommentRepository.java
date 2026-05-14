package io.pinkspider.leveluptogethermvp.feedservice.infrastructure;

import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedComment;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface FeedCommentRepository extends JpaRepository<FeedComment, Long> {

    @Query("SELECT c FROM FeedComment c WHERE c.feed.id = :feedId AND c.isDeleted = false " +
           "ORDER BY c.createdAt ASC")
    Page<FeedComment> findByFeedId(@Param("feedId") Long feedId, Pageable pageable);

    @Query("SELECT c FROM FeedComment c WHERE c.feed.id = :feedId AND c.isDeleted = false " +
           "ORDER BY c.createdAt ASC")
    List<FeedComment> findAllByFeedId(@Param("feedId") Long feedId);

    @Query("SELECT COUNT(c) FROM FeedComment c WHERE c.feed.id = :feedId AND c.isDeleted = false")
    int countByFeedId(@Param("feedId") Long feedId);

    /**
     * 최상위 댓글만 페이징 (부모가 없는 댓글). 트리 응답에서 부모 단위로 페이징 처리.
     * 삭제된 댓글도 대댓글 보존을 위해 포함시킨다 (content는 "[삭제된 댓글입니다]"로 표시됨).
     */
    @Query("SELECT c FROM FeedComment c WHERE c.feed.id = :feedId AND c.parent IS NULL " +
           "ORDER BY c.createdAt ASC")
    Page<FeedComment> findRootCommentsByFeedId(@Param("feedId") Long feedId, Pageable pageable);

    /**
     * 여러 부모 댓글의 대댓글을 한 번에 조회 (N+1 방지).
     */
    @Query("SELECT c FROM FeedComment c WHERE c.parent.id IN :parentIds " +
           "ORDER BY c.createdAt ASC")
    List<FeedComment> findRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    /**
     * 특정 부모 댓글의 활성(미삭제) 대댓글 수.
     * "대댓글이 달린 댓글은 수정 불가" 규칙을 검증할 때 사용.
     */
    @Query("SELECT COUNT(c) FROM FeedComment c WHERE c.parent.id = :parentId AND c.isDeleted = false")
    int countActiveRepliesByParentId(@Param("parentId") Long parentId);

    /**
     * 특정 부모에 대댓글을 남긴 유저 ID 목록 (자기 자신/중복 알림 dedup용).
     */
    @Query("SELECT DISTINCT c.userId FROM FeedComment c WHERE c.parent.id = :parentId AND c.isDeleted = false")
    List<String> findReplyAuthorsByParentId(@Param("parentId") Long parentId);

    @Modifying
    @Transactional(transactionManager = "feedTransactionManager")
    @Query("UPDATE FeedComment c SET c.userNickname = :nickname, c.userProfileImageUrl = :profileImageUrl, c.userLevel = :level WHERE c.userId = :userId")
    int updateUserProfileByUserId(
        @Param("userId") String userId,
        @Param("nickname") String nickname,
        @Param("profileImageUrl") String profileImageUrl,
        @Param("level") Integer level);
}
