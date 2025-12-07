package io.pinkspider.leveluptogethermvp.userservice.feed.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.feed.domain.entity.FeedLike;
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
}
