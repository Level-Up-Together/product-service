package io.pinkspider.leveluptogethermvp.userservice.feed.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.feed.domain.entity.FeedComment;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
