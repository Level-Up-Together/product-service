package io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.DailyMvpExclusion;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyMvpExclusionRepository extends JpaRepository<DailyMvpExclusion, Long> {

    @Query("SELECT e.userId FROM DailyMvpExclusion e WHERE e.mvpDate = :date")
    List<String> findExcludedUserIdsByDate(@Param("date") LocalDate date);

    boolean existsByMvpDateAndUserId(LocalDate mvpDate, String userId);

    List<DailyMvpExclusion> findAllByMvpDateOrderByCreatedAtDesc(LocalDate mvpDate);

    void deleteByMvpDateAndUserId(LocalDate mvpDate, String userId);
}
