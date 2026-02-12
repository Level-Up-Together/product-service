package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserCategoryExperience;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserCategoryExperienceRepository extends JpaRepository<UserCategoryExperience, Long> {

    /**
     * 사용자의 특정 카테고리 경험치 조회
     */
    Optional<UserCategoryExperience> findByUserIdAndCategoryId(String userId, Long categoryId);

    /**
     * 사용자의 모든 카테고리별 경험치 조회
     */
    List<UserCategoryExperience> findByUserIdOrderByTotalExpDesc(String userId);

    /**
     * 특정 카테고리의 경험치 랭킹 조회 (상위 N명)
     */
    Page<UserCategoryExperience> findByCategoryIdOrderByTotalExpDesc(Long categoryId, Pageable pageable);

    /**
     * 특정 카테고리에서 사용자보다 경험치가 높은 사용자 수 (순위 계산용)
     */
    @Query("""
        SELECT COUNT(uce)
        FROM UserCategoryExperience uce
        WHERE uce.categoryId = :categoryId
        AND uce.totalExp > :userExp
        """)
    long countUsersWithMoreExpInCategory(
        @Param("categoryId") Long categoryId,
        @Param("userExp") Long userExp);

    /**
     * 특정 카테고리의 총 사용자 수
     */
    long countByCategoryId(Long categoryId);

    /**
     * 사용자의 카테고리별 경험치 합계 (전체 경험치와 일치 검증용)
     */
    @Query("SELECT COALESCE(SUM(uce.totalExp), 0) FROM UserCategoryExperience uce WHERE uce.userId = :userId")
    Long sumTotalExpByUserId(@Param("userId") String userId);

    /**
     * 특정 카테고리 경험치 상위 N명 조회 (userId와 totalExp만)
     */
    @Query("""
        SELECT uce.userId, uce.totalExp
        FROM UserCategoryExperience uce
        WHERE uce.categoryId = :categoryId
        ORDER BY uce.totalExp DESC
        """)
    List<Object[]> findTopUsersByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    /**
     * 여러 사용자의 특정 카테고리 경험치 조회
     */
    @Query("""
        SELECT uce
        FROM UserCategoryExperience uce
        WHERE uce.userId IN :userIds
        AND uce.categoryId = :categoryId
        """)
    List<UserCategoryExperience> findByUserIdInAndCategoryId(
        @Param("userIds") List<String> userIds,
        @Param("categoryId") Long categoryId);
}
