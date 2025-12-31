package io.pinkspider.leveluptogethermvp.metaservice.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.FeaturedGuild;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeaturedGuildRepository extends JpaRepository<FeaturedGuild, Long> {

    /**
     * 특정 카테고리의 활성화된 추천 길드 목록 조회 (현재 시간 기준)
     */
    @Query("""
        SELECT fg FROM FeaturedGuild fg
        WHERE fg.categoryId = :categoryId
        AND fg.isActive = true
        AND (fg.startAt IS NULL OR fg.startAt <= :now)
        AND (fg.endAt IS NULL OR fg.endAt > :now)
        ORDER BY fg.displayOrder ASC
        """)
    List<FeaturedGuild> findActiveFeaturedGuilds(
        @Param("categoryId") Long categoryId,
        @Param("now") LocalDateTime now);

    /**
     * 특정 카테고리의 모든 추천 길드 목록 조회 (관리용)
     */
    List<FeaturedGuild> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);

    /**
     * 페이징된 추천 길드 목록 조회
     */
    Page<FeaturedGuild> findByCategoryId(Long categoryId, Pageable pageable);

    /**
     * 전체 페이징된 추천 길드 목록 조회
     */
    Page<FeaturedGuild> findAll(Pageable pageable);

    /**
     * 특정 길드가 해당 카테고리에 이미 추천으로 등록되어 있는지 확인
     */
    boolean existsByCategoryIdAndGuildId(Long categoryId, Long guildId);

    /**
     * 특정 카테고리의 추천 길드 수 조회
     */
    long countByCategoryId(Long categoryId);

    /**
     * 추천 길드 ID 목록 조회 (하이브리드 선정 시 중복 제거용)
     */
    @Query("""
        SELECT fg.guildId FROM FeaturedGuild fg
        WHERE fg.categoryId = :categoryId
        AND fg.isActive = true
        AND (fg.startAt IS NULL OR fg.startAt <= :now)
        AND (fg.endAt IS NULL OR fg.endAt > :now)
        """)
    List<Long> findActiveGuildIds(
        @Param("categoryId") Long categoryId,
        @Param("now") LocalDateTime now);
}
