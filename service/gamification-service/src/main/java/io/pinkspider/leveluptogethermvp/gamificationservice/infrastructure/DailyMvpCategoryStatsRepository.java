package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.DailyMvpCategoryStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyMvpCategoryStatsRepository extends JpaRepository<DailyMvpCategoryStats, Long> {

    /**
     * 특정 날짜의 카테고리 통계 조회
     */
    List<DailyMvpCategoryStats> findByStatsDate(LocalDate statsDate);

    /**
     * 특정 날짜에 데이터 존재 여부 확인
     */
    boolean existsByStatsDate(LocalDate statsDate);

    /**
     * 특정 날짜의 데이터 삭제 (재처리용)
     */
    void deleteByStatsDate(LocalDate statsDate);

    /**
     * 카테고리별 총 경험치 통계 (기간)
     */
    @Query("""
        SELECT d.categoryId, d.categoryName, SUM(d.earnedExp) as totalExp,
               SUM(d.activityCount) as totalActivity, COUNT(DISTINCT d.userId) as uniqueUsers
        FROM DailyMvpCategoryStats d
        WHERE d.statsDate >= :startDate AND d.statsDate <= :endDate
        GROUP BY d.categoryId, d.categoryName
        ORDER BY totalExp DESC
        """)
    List<Object[]> getCategoryStatsByPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    /**
     * 특정 사용자의 카테고리별 통계 (기간)
     */
    @Query("""
        SELECT d.categoryId, d.categoryName, SUM(d.earnedExp) as totalExp,
               SUM(d.activityCount) as totalActivity
        FROM DailyMvpCategoryStats d
        WHERE d.userId = :userId
        AND d.statsDate >= :startDate AND d.statsDate <= :endDate
        GROUP BY d.categoryId, d.categoryName
        ORDER BY totalExp DESC
        """)
    List<Object[]> getUserCategoryStatsByPeriod(
        @Param("userId") String userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    /**
     * 일별 카테고리 인기도 추이 (기간)
     */
    @Query("""
        SELECT d.statsDate, d.categoryId, d.categoryName, SUM(d.earnedExp) as totalExp
        FROM DailyMvpCategoryStats d
        WHERE d.statsDate >= :startDate AND d.statsDate <= :endDate
        GROUP BY d.statsDate, d.categoryId, d.categoryName
        ORDER BY d.statsDate ASC, totalExp DESC
        """)
    List<Object[]> getDailyCategoryTrend(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
}
