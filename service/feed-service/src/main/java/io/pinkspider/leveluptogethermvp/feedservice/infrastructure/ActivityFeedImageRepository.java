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

    /**
     * LUT-385: 반드시 벌크 삭제(@Query)여야 한다. 파생 삭제(deleteBy...)는 엔티티 로드 후 제거를
     * ActionQueue에 쌓는데, Hibernate flush 순서상 INSERT가 DELETE보다 먼저 실행되어
     * "전체 교체(삭제 후 재등록)" 시 기존 행과 (feed_id, sort_order) 유니크 제약이 충돌한다.
     * 벌크 삭제는 호출 즉시 실행되므로 이후 INSERT가 안전하다.
     */
    @Modifying(flushAutomatically = true)
    @Transactional(transactionManager = "feedTransactionManager")
    @Query("DELETE FROM ActivityFeedImage i WHERE i.feed.id = :feedId")
    void deleteByFeedId(@Param("feedId") Long feedId);
}
