package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorVisit;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TowerFloorVisitRepository extends JpaRepository<TowerFloorVisit, Long> {

    /**
     * LUT-634: 첫 입장이면 1, 이미 있으면 0. 같은 유저가 동시에 두 번 들어와도 유니크 제약이 한 건만 받는다 — 예외 대신 0행이라 입장 트랜잭션을 오염시키지
     * 않는다(LUT-719 패턴).
     */
    @Modifying
    @Query(
            value =
                    "INSERT INTO tower_floor_visit"
                            + " (user_id, floor_number, first_entered_at, created_at, modified_at)"
                            + " VALUES (:userId, :floorNumber, :at, :at, :at)"
                            + " ON CONFLICT (user_id, floor_number) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") String userId,
            @Param("floorNumber") int floorNumber,
            @Param("at") LocalDateTime at);

    long countByUserId(String userId);

    @Query("select coalesce(max(v.floorNumber), 0) from TowerFloorVisit v where v.userId = :userId")
    int findHighestFloorNumber(@Param("userId") String userId);
}
