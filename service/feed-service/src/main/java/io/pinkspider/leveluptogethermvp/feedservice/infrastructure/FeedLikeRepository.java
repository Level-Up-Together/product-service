package io.pinkspider.leveluptogethermvp.feedservice.infrastructure;

import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.FeedLike;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedLikeRepository extends JpaRepository<FeedLike, Long> {

    Optional<FeedLike> findByFeedIdAndUserId(Long feedId, String userId);

    boolean existsByFeedIdAndUserId(Long feedId, String userId);

    @Query("SELECT fl.feed.id FROM FeedLike fl WHERE fl.userId = :userId AND fl.feed.id IN :feedIds")
    List<Long> findLikedFeedIds(@Param("userId") String userId, @Param("feedIds") List<Long> feedIds);

    int countByFeedId(Long feedId);

    /**
     * 특정 유저가 작성한 피드들에 받은 총 좋아요 수
     */
    @Query("SELECT COUNT(fl) FROM FeedLike fl WHERE fl.feed.userId = :userId")
    long countLikesReceivedByUser(@Param("userId") String userId);
}
