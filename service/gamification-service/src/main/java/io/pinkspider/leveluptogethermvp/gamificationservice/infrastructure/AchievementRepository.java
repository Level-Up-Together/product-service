package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    List<Achievement> findByIsActiveTrue();

    List<Achievement> findByIsActiveTrueOrderByIdAsc();

    /**
     * 카테고리 코드로 활성 업적 조회
     */
    List<Achievement> findByCategoryCodeAndIsActiveTrue(String categoryCode);

    /**
     * 미션 카테고리 ID로 활성 업적 조회
     */
    List<Achievement> findByMissionCategoryIdAndIsActiveTrue(Long missionCategoryId);

    @Query("SELECT a FROM Achievement a WHERE a.isActive = true AND a.isHidden = false")
    List<Achievement> findVisibleAchievements();

    @Query("SELECT a FROM Achievement a WHERE a.isActive = true AND a.isHidden = false AND a.categoryCode = :categoryCode")
    List<Achievement> findVisibleAchievementsByCategoryCode(@Param("categoryCode") String categoryCode);

    @Query("SELECT a FROM Achievement a WHERE a.isActive = true AND a.isHidden = false AND a.missionCategoryId = :missionCategoryId")
    List<Achievement> findVisibleAchievementsByMissionCategoryId(@Param("missionCategoryId") Long missionCategoryId);

    /**
     * 체크 로직 데이터 소스로 활성 업적 조회 (동적 체크용)
     */
    @Query("SELECT a FROM Achievement a WHERE a.isActive = true AND a.checkLogicDataSource = :dataSource")
    List<Achievement> findByCheckLogicDataSourceAndIsActiveTrue(@Param("dataSource") String dataSource);

    /**
     * 체크 로직이 설정된 모든 활성 업적 조회
     */
    @Query("SELECT a FROM Achievement a WHERE a.isActive = true AND a.checkLogicDataSource IS NOT NULL")
    List<Achievement> findAllWithCheckLogicAndIsActiveTrue();

    /**
     * 카테고리 코드로 업적 조회 (카테고리 코드 캐스케이드용)
     */
    List<Achievement> findByCategoryCode(String categoryCode);

    /**
     * 키워드 검색 (이름/설명 기반, 페이징)
     */
    @Query("SELECT a FROM Achievement a WHERE " +
        "(:keyword IS NULL OR :keyword = '' OR " +
        "LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
        "LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Achievement> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 키워드 + 카테고리 필터 검색 (페이징)
     */
    @Query("SELECT a FROM Achievement a WHERE " +
        "(:keyword IS NULL OR :keyword = '' OR " +
        "LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
        "LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
        "AND (:hasCategoryFilter = false OR a.category.id IN :categoryIds)")
    Page<Achievement> searchByKeywordAndCategoryIds(
        @Param("keyword") String keyword,
        @Param("hasCategoryFilter") boolean hasCategoryFilter,
        @Param("categoryIds") java.util.List<Long> categoryIds,
        Pageable pageable);

    @Deprecated(forRemoval = false)
    @Query("SELECT a FROM Achievement a WHERE " +
        "(:keyword IS NULL OR :keyword = '' OR " +
        "LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
        "LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
        "AND (:categoryId IS NULL OR a.category.id = :categoryId)")
    Page<Achievement> searchByKeywordAndCategoryId(
        @Param("keyword") String keyword,
        @Param("categoryId") Long categoryId,
        Pageable pageable);

    /**
     * 보상 칭호 ID로 업적 조회
     */
    List<Achievement> findByRewardTitleId(Long rewardTitleId);

    /**
     * 공개 업적 조회 (활성 + 비숨김, ID순)
     */
    @Query("SELECT a FROM Achievement a WHERE a.isActive = true AND a.isHidden = false ORDER BY a.id ASC")
    List<Achievement> findVisibleAchievementsOrderByIdAsc();

    /**
     * QA-154: 특정 check_logic_type 을 참조하는 업적 개수.
     * 삭제 가드 + 운영자 안내 메시지에 사용.
     */
    long countByCheckLogicTypeId(Long checkLogicTypeId);
}
