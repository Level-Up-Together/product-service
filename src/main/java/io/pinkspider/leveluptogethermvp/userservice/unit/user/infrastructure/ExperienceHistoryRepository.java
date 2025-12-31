package io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.ExperienceHistory;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.ExperienceHistory.ExpSourceType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExperienceHistoryRepository extends JpaRepository<ExperienceHistory, Long> {

    Page<ExperienceHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<ExperienceHistory> findByUserIdAndSourceType(String userId, ExpSourceType sourceType);

    @Query("SELECT SUM(eh.expAmount) FROM ExperienceHistory eh WHERE eh.userId = :userId")
    Long sumExpByUserId(@Param("userId") String userId);

    @Query("SELECT SUM(eh.expAmount) FROM ExperienceHistory eh WHERE eh.userId = :userId AND eh.sourceType = :sourceType")
    Long sumExpByUserIdAndSourceType(@Param("userId") String userId, @Param("sourceType") ExpSourceType sourceType);

    /**
     * 특정 기간 동안 가장 많은 경험치를 획득한 사용자 목록 (Top N)
     * 오늘의 플레이어 기능에 사용 (어제 00:00 ~ 23:59 기준)
     */
    @Query("""
        SELECT eh.userId, SUM(eh.expAmount) as totalExp
        FROM ExperienceHistory eh
        WHERE eh.createdAt >= :startDate AND eh.createdAt < :endDate
        GROUP BY eh.userId
        ORDER BY totalExp DESC
        """)
    List<Object[]> findTopExpGainersByPeriod(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable);

    /**
     * 카테고리별 경험치 합계로 사용자 랭킹 조회
     * 카테고리별 레벨 랭킹 기능에 사용
     */
    @Query("""
        SELECT eh.userId, SUM(eh.expAmount) as totalCategoryExp
        FROM ExperienceHistory eh
        WHERE eh.categoryName = :categoryName
        AND eh.expAmount > 0
        GROUP BY eh.userId
        ORDER BY totalCategoryExp DESC
        """)
    Page<Object[]> findUserExpRankingByCategory(
        @Param("categoryName") String categoryName,
        Pageable pageable);

    /**
     * 특정 카테고리의 전체 사용자 수 조회
     */
    @Query("""
        SELECT COUNT(DISTINCT eh.userId)
        FROM ExperienceHistory eh
        WHERE eh.categoryName = :categoryName
        AND eh.expAmount > 0
        """)
    long countUsersByCategory(@Param("categoryName") String categoryName);

    /**
     * 특정 카테고리 + 기간 동안 가장 많은 경험치를 획득한 사용자 목록 (Top N)
     * 카테고리별 오늘의 플레이어 기능에 사용
     */
    @Query("""
        SELECT eh.userId, SUM(eh.expAmount) as totalExp
        FROM ExperienceHistory eh
        WHERE eh.categoryName = :categoryName
        AND eh.createdAt >= :startDate AND eh.createdAt < :endDate
        AND eh.expAmount > 0
        GROUP BY eh.userId
        ORDER BY totalExp DESC
        """)
    List<Object[]> findTopExpGainersByCategoryAndPeriod(
        @Param("categoryName") String categoryName,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable);
}
