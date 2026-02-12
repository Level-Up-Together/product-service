package io.pinkspider.leveluptogethermvp.adminservice.infrastructure;

import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.FeaturedPlayer;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeaturedPlayerRepository extends JpaRepository<FeaturedPlayer, Long> {

    /**
     * 특정 카테고리의 활성화된 추천 플레이어 목록 조회 (현재 시간 기준)
     */
    @Query("""
        SELECT fp FROM FeaturedPlayer fp
        WHERE fp.categoryId = :categoryId
        AND fp.isActive = true
        AND (fp.startAt IS NULL OR fp.startAt <= :now)
        AND (fp.endAt IS NULL OR fp.endAt > :now)
        ORDER BY fp.displayOrder ASC
        """)
    List<FeaturedPlayer> findActiveFeaturedPlayers(
        @Param("categoryId") Long categoryId,
        @Param("now") LocalDateTime now);

    /**
     * 특정 카테고리의 모든 추천 플레이어 목록 조회 (관리용)
     */
    List<FeaturedPlayer> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);

    /**
     * 페이징된 추천 플레이어 목록 조회
     */
    Page<FeaturedPlayer> findByCategoryId(Long categoryId, Pageable pageable);

    /**
     * 전체 페이징된 추천 플레이어 목록 조회
     */
    Page<FeaturedPlayer> findAll(Pageable pageable);

    /**
     * 특정 사용자가 해당 카테고리에 이미 추천으로 등록되어 있는지 확인
     */
    boolean existsByCategoryIdAndUserId(Long categoryId, String userId);

    /**
     * 특정 카테고리의 추천 플레이어 수 조회
     */
    long countByCategoryId(Long categoryId);
}
