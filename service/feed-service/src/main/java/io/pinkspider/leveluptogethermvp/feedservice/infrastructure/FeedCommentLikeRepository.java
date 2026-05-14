package io.pinkspider.leveluptogethermvp.feedservice.infrastructure;

import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedCommentLike;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedCommentLikeRepository extends JpaRepository<FeedCommentLike, Long> {

    Optional<FeedCommentLike> findByCommentIdAndUserId(Long commentId, String userId);

    boolean existsByCommentIdAndUserId(Long commentId, String userId);

    int countByCommentId(Long commentId);

    /**
     * 여러 댓글에 대한 좋아요 수를 한 번에 조회 (트리 응답 시 N+1 방지).
     * 반환: [commentId, count]
     */
    @Query("SELECT fcl.comment.id, COUNT(fcl) FROM FeedCommentLike fcl " +
           "WHERE fcl.comment.id IN :commentIds GROUP BY fcl.comment.id")
    List<Object[]> countByCommentIds(@Param("commentIds") List<Long> commentIds);

    /**
     * 특정 유저가 좋아요 누른 댓글 ID 목록 (트리 응답 시 N+1 방지).
     */
    @Query("SELECT fcl.comment.id FROM FeedCommentLike fcl " +
           "WHERE fcl.userId = :userId AND fcl.comment.id IN :commentIds")
    List<Long> findLikedCommentIds(@Param("userId") String userId, @Param("commentIds") List<Long> commentIds);
}
