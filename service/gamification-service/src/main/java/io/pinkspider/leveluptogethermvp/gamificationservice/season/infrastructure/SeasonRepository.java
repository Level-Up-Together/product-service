package io.pinkspider.leveluptogethermvp.gamificationservice.season.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.Season;
import io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.entity.SeasonRewardHistory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SeasonRepository extends JpaRepository<Season, Long> {

    /**
     * 현재 활성 시즌 조회
     */
    @Query("SELECT s FROM Season s WHERE s.isActive = true AND s.startAt <= :now AND s.endAt >= :now ORDER BY s.sortOrder ASC")
    Optional<Season> findCurrentSeason(@Param("now") LocalDateTime now);

    /**
     * 종료된 시즌 중 보상 미처리 시즌 조회
     */
    @Query("""
        SELECT s FROM Season s
        WHERE s.isActive = true
        AND s.endAt < :now
        AND s.id NOT IN (SELECT DISTINCT srh.seasonId FROM SeasonRewardHistory srh)
        ORDER BY s.endAt DESC
        """)
    List<Season> findEndedSeasonsWithoutRewards(@Param("now") LocalDateTime now);

    /**
     * 아직 종료되지 않은 활성 시즌 조회 (서버 시작 시 단발 작업 등록용)
     */
    @Query("""
        SELECT s FROM Season s
        WHERE s.isActive = true
        AND s.endAt >= :now
        ORDER BY s.endAt ASC
        """)
    List<Season> findFutureActiveSeasons(@Param("now") LocalDateTime now);

    // ===== Admin API용 쿼리 =====

    List<Season> findAllByOrderBySortOrderAscStartAtDesc();

    Page<Season> findAllByOrderBySortOrderAscStartAtDesc(Pageable pageable);

    @Query("SELECT s FROM Season s WHERE s.title LIKE %:keyword% OR s.description LIKE %:keyword% ORDER BY s.sortOrder ASC, s.startAt DESC")
    Page<Season> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT s FROM Season s WHERE s.isActive = true AND s.startAt > :now ORDER BY s.startAt ASC")
    List<Season> findUpcomingSeasons(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(s) > 0 FROM Season s WHERE s.isActive = true AND s.id != :excludeId AND s.startAt <= :endAt AND s.endAt >= :startAt")
    boolean existsOverlappingActiveSeason(
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt,
        @Param("excludeId") Long excludeId
    );

    @Query("SELECT COUNT(s) > 0 FROM Season s WHERE s.isActive = true AND s.startAt <= :endAt AND s.endAt >= :startAt")
    boolean existsOverlappingActiveSeasonForNew(
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );
}
