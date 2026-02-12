package io.pinkspider.leveluptogethermvp.adminservice.infrastructure;

import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.FeaturedFeed;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeaturedFeedRepository extends JpaRepository<FeaturedFeed, Long> {

    /**
     * 특정 카테고리의 활성화된 추천 피드 목록 조회 (현재 시간 기준)
     */
    @Query("""
        SELECT ff FROM FeaturedFeed ff
        WHERE ff.categoryId = :categoryId
        AND ff.isActive = true
        AND (ff.startAt IS NULL OR ff.startAt <= :now)
        AND (ff.endAt IS NULL OR ff.endAt > :now)
        ORDER BY ff.displayOrder ASC
        """)
    List<FeaturedFeed> findActiveFeaturedFeeds(
        @Param("categoryId") Long categoryId,
        @Param("now") LocalDateTime now);

    /**
     * 글로벌 추천 피드만 조회 (카테고리 무관)
     */
    @Query("""
        SELECT ff FROM FeaturedFeed ff
        WHERE ff.categoryId IS NULL
        AND ff.isActive = true
        AND (ff.startAt IS NULL OR ff.startAt <= :now)
        AND (ff.endAt IS NULL OR ff.endAt > :now)
        ORDER BY ff.displayOrder ASC
        """)
    List<FeaturedFeed> findGlobalFeaturedFeeds(@Param("now") LocalDateTime now);

    /**
     * 특정 카테고리의 모든 추천 피드 목록 조회 (관리용)
     */
    List<FeaturedFeed> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);

    /**
     * 글로벌 추천 피드 목록 조회 (관리용)
     */
    List<FeaturedFeed> findByCategoryIdIsNullOrderByDisplayOrderAsc();

    /**
     * 페이징된 추천 피드 목록 조회
     */
    Page<FeaturedFeed> findByCategoryId(Long categoryId, Pageable pageable);

    /**
     * 전체 페이징된 추천 피드 목록 조회
     */
    Page<FeaturedFeed> findAll(Pageable pageable);

    /**
     * 특정 피드가 해당 카테고리에 이미 추천으로 등록되어 있는지 확인
     */
    boolean existsByCategoryIdAndFeedId(Long categoryId, Long feedId);

    /**
     * 특정 카테고리의 추천 피드 수 조회
     */
    long countByCategoryId(Long categoryId);

    /**
     * 추천 피드 ID 목록 조회 (하이브리드 선정 시 중복 제거용)
     */
    @Query("""
        SELECT ff.feedId FROM FeaturedFeed ff
        WHERE ff.categoryId = :categoryId
        AND ff.isActive = true
        AND (ff.startAt IS NULL OR ff.startAt <= :now)
        AND (ff.endAt IS NULL OR ff.endAt > :now)
        """)
    List<Long> findActiveFeedIds(
        @Param("categoryId") Long categoryId,
        @Param("now") LocalDateTime now);
}
