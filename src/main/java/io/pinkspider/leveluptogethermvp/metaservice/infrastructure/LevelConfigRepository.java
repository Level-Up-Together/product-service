package io.pinkspider.leveluptogethermvp.metaservice.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.LevelConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LevelConfigRepository extends JpaRepository<LevelConfig, Long> {

    Optional<LevelConfig> findByLevel(Integer level);

    List<LevelConfig> findAllByOrderByLevelAsc();

    @Query("SELECT lc FROM LevelConfig lc WHERE lc.level = " +
           "(SELECT MAX(lc2.level) FROM LevelConfig lc2 WHERE lc2.cumulativeExp <= :totalExp)")
    Optional<LevelConfig> findLevelByTotalExp(@Param("totalExp") Integer totalExp);

    @Query("SELECT MAX(lc.level) FROM LevelConfig lc")
    Integer findMaxLevel();
}
