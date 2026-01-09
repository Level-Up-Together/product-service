package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.AchievementCategory;
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

    List<Achievement> findByCategoryAndIsActiveTrue(AchievementCategory category);

    @Query("SELECT a FROM Achievement a WHERE a.isActive = true AND a.isHidden = false")
    List<Achievement> findVisibleAchievements();

    @Query("SELECT a FROM Achievement a WHERE a.isActive = true AND a.isHidden = false AND a.category = :category")
    List<Achievement> findVisibleAchievementsByCategory(@Param("category") AchievementCategory category);
}
