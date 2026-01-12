package io.pinkspider.leveluptogethermvp.adminservice.infrastructure;

import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SeasonRepository extends JpaRepository<Season, Long> {

    /**
     * 현재 활성 시즌 조회
     */
    @Query("SELECT s FROM Season s WHERE s.isActive = true AND s.startAt <= :now AND s.endAt >= :now ORDER BY s.sortOrder ASC")
    Optional<Season> findCurrentSeason(@Param("now") LocalDateTime now);
}
