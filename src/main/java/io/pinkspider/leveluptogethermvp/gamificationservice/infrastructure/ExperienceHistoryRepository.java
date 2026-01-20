package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.ExperienceHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ExpSourceType;
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
     * categoryName이 있는 경험치만 포함 (모든 카테고리 MVP)
     */
    @Query("""
        SELECT eh.userId, SUM(eh.expAmount) as totalExp
        FROM ExperienceHistory eh
        WHERE eh.createdAt >= :startDate AND eh.createdAt < :endDate
        AND eh.categoryName IS NOT NULL
        AND eh.expAmount > 0
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

    /**
     * 특정 기간 동안 사용자의 총 경험치 합계 조회
     */
    @Query("""
        SELECT COALESCE(SUM(eh.expAmount), 0)
        FROM ExperienceHistory eh
        WHERE eh.userId = :userId
        AND eh.createdAt >= :startDate AND eh.createdAt < :endDate
        AND eh.expAmount > 0
        """)
    Long sumExpByUserIdAndPeriod(
        @Param("userId") String userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * 특정 기간 동안 사용자보다 경험치가 많은 사용자 수 조회 (순위 계산용)
     */
    @Query(value = """
        SELECT COUNT(*) FROM (
            SELECT eh.user_id, SUM(eh.exp_amount) as total_exp
            FROM experience_history eh
            WHERE eh.created_at >= :startDate AND eh.created_at < :endDate
            AND eh.category_name IS NOT NULL
            AND eh.exp_amount > 0
            GROUP BY eh.user_id
            HAVING SUM(eh.exp_amount) > :myExp
        ) sub
        """, nativeQuery = true)
    Long countUsersWithMoreExpByPeriod(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("myExp") Long myExp);

    /**
     * 특정 기간 + 카테고리 동안 사용자의 총 경험치 합계 조회
     */
    @Query("""
        SELECT COALESCE(SUM(eh.expAmount), 0)
        FROM ExperienceHistory eh
        WHERE eh.userId = :userId
        AND eh.categoryName = :categoryName
        AND eh.createdAt >= :startDate AND eh.createdAt < :endDate
        AND eh.expAmount > 0
        """)
    Long sumExpByUserIdAndCategoryAndPeriod(
        @Param("userId") String userId,
        @Param("categoryName") String categoryName,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * 특정 기간 + 카테고리 동안 사용자보다 경험치가 많은 사용자 수 조회 (순위 계산용)
     */
    @Query(value = """
        SELECT COUNT(*) FROM (
            SELECT eh.user_id, SUM(eh.exp_amount) as total_exp
            FROM experience_history eh
            WHERE eh.category_name = :categoryName
            AND eh.created_at >= :startDate AND eh.created_at < :endDate
            AND eh.exp_amount > 0
            GROUP BY eh.user_id
            HAVING SUM(eh.exp_amount) > :myExp
        ) sub
        """, nativeQuery = true)
    Long countUsersWithMoreExpByCategoryAndPeriod(
        @Param("categoryName") String categoryName,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("myExp") Long myExp);

    /**
     * 특정 사용자의 카테고리별 경험치 통계 (기간)
     * MVP 히스토리 저장 시 사용
     */
    @Query("""
        SELECT eh.categoryName, eh.categoryName as categoryId,
               SUM(eh.expAmount) as totalExp, COUNT(eh) as activityCount
        FROM ExperienceHistory eh
        WHERE eh.userId = :userId
        AND eh.createdAt >= :startDate AND eh.createdAt < :endDate
        AND eh.categoryName IS NOT NULL
        AND eh.expAmount > 0
        GROUP BY eh.categoryName
        ORDER BY totalExp DESC
        """)
    List<Object[]> findUserCategoryExpByPeriod(
        @Param("userId") String userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
}
