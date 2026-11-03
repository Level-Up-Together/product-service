package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerMissionSeat;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TowerMissionSeatRepository extends JpaRepository<TowerMissionSeat, Long> {

    List<TowerMissionSeat> findAllByUserIdAndEndedAtIsNull(String userId);

    Optional<TowerMissionSeat> findFirstByUserIdAndEndedAtIsNullOrderBySeatedAtDesc(String userId);

    Optional<TowerMissionSeat> findFirstByUserIdAndExecutionKindAndExecutionIdOrderBySeatedAtDesc(
            String userId, String executionKind, Long executionId);

    /** 응원 수 +1 — 여러 유저가 동시에 응원해도 잃어버리지 않게 DB 에서 더한다 */
    @Modifying
    @Query("update TowerMissionSeat s set s.cheerCount = s.cheerCount + 1 where s.id = :id")
    int incrementCheer(@Param("id") Long id);
}
