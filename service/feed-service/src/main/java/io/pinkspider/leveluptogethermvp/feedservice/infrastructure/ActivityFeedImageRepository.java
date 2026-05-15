package io.pinkspider.leveluptogethermvp.feedservice.infrastructure;

import io.pinkspider.leveluptogethermvp.feedservice.domain.entity.ActivityFeedImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ActivityFeedImageRepository extends JpaRepository<ActivityFeedImage, Long> {

    List<ActivityFeedImage> findByFeedIdOrderBySortOrderAsc(Long feedId);

    /** 피드 목록 응답에서 N+1 방지용 배치 조회. */
    @Query("SELECT afi FROM ActivityFeedImage afi " +
           "WHERE afi.feed.id IN :feedIds ORDER BY afi.feed.id, afi.sortOrder ASC")
    List<ActivityFeedImage> findByFeedIdInOrderBySortOrder(@Param("feedIds") List<Long> feedIds);

    int countByFeedId(Long feedId);

    @Modifying
    @Transactional(transactionManager = "feedTransactionManager")
    void deleteByFeedId(Long feedId);
}
