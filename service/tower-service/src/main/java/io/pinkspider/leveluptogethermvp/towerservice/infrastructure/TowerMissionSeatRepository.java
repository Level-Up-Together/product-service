package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerMissionSeat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

    /** LUT-693 실제 흔적: 기간 안에 이 층에서 미션을 완료한 사람 수(같은 사람이 여러 번 완료해도 1) */
    @Query(
            "select count(distinct s.userId) from TowerMissionSeat s where s.floorNumber = :floor"
                    + " and s.completedAt >= :from and s.completedAt < :to")
    long countCompletedUsers(
            @Param("floor") Integer floorNumber,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** LUT-693 실제 흔적: 기간 안에 이 층에서 미션을 완료한 자리(좌표가 있는 것만, 최근 순) */
    @Query(
            "select s from TowerMissionSeat s where s.floorNumber = :floor and s.completedAt >="
                + " :from and s.completedAt < :to and s.seatX is not null and s.seatY is not null"
                + " order by s.completedAt desc")
    List<TowerMissionSeat> findCompletedWithSeat(
            @Param("floor") Integer floorNumber,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
