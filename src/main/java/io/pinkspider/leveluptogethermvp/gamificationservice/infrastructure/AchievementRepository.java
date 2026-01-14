package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.AchievementType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    Optional<Achievement> findByAchievementType(AchievementType type);

    List<Achievement> findByIsActiveTrue();

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
}
