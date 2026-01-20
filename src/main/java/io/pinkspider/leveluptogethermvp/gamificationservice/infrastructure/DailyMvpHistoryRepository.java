package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.DailyMvpHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyMvpHistoryRepository extends JpaRepository<DailyMvpHistory, Long> {

    /**
     * 특정 날짜의 MVP 목록 조회
     */
    List<DailyMvpHistory> findByMvpDateOrderByMvpRankAsc(LocalDate mvpDate);

    /**
     * 특정 사용자의 MVP 히스토리 조회
     */
    Page<DailyMvpHistory> findByUserIdOrderByMvpDateDesc(String userId, Pageable pageable);

    /**
     * 기간별 MVP 히스토리 조회
     */
    @Query("""
        SELECT d FROM DailyMvpHistory d
        WHERE d.mvpDate >= :startDate AND d.mvpDate <= :endDate
        ORDER BY d.mvpDate DESC, d.mvpRank ASC
        """)
    List<DailyMvpHistory> findByPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    /**
     * 기간별 MVP 히스토리 조회 (페이징)
     */
    @Query("""
        SELECT d FROM DailyMvpHistory d
        WHERE d.mvpDate >= :startDate AND d.mvpDate <= :endDate
        ORDER BY d.mvpDate DESC, d.mvpRank ASC
        """)
    Page<DailyMvpHistory> findByPeriodPaged(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable);

    /**
     * 특정 날짜에 데이터 존재 여부 확인
     */
    boolean existsByMvpDate(LocalDate mvpDate);

    /**
     * 특정 날짜의 데이터 삭제 (재처리용)
     */
    void deleteByMvpDate(LocalDate mvpDate);

    /**
     * 사용자별 MVP 선정 횟수 통계 (기간)
     */
    @Query("""
        SELECT d.userId, d.nickname, COUNT(d) as mvpCount,
               SUM(CASE WHEN d.mvpRank = 1 THEN 1 ELSE 0 END) as rank1Count
        FROM DailyMvpHistory d
        WHERE d.mvpDate >= :startDate AND d.mvpDate <= :endDate
        GROUP BY d.userId, d.nickname
        ORDER BY mvpCount DESC
        """)
    List<Object[]> countMvpByUserAndPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable);

    /**
     * 사용자별 MVP 선정 횟수 (순위별)
     */
    @Query("""
        SELECT d.userId, d.mvpRank, COUNT(d) as mvpCount
        FROM DailyMvpHistory d
        WHERE d.mvpDate >= :startDate AND d.mvpDate <= :endDate
        GROUP BY d.userId, d.mvpRank
        ORDER BY d.userId, d.mvpRank
        """)
    List<Object[]> countMvpByUserAndRankAndPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
}
