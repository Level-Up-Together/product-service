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

    @Modifying
    @Transactional(transactionManager = "feedTransactionManager")
    @Query("UPDATE FeedComment c SET c.userNickname = :nickname, c.userProfileImageUrl = :profileImageUrl, c.userLevel = :level WHERE c.userId = :userId")
    int updateUserProfileByUserId(
        @Param("userId") String userId,
        @Param("nickname") String nickname,
        @Param("profileImageUrl") String profileImageUrl,
        @Param("level") Integer level);
}
