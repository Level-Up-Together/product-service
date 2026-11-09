package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TowerOccupantRepository extends JpaRepository<TowerOccupant, Long> {

    Optional<TowerOccupant> findByUserId(String userId);

    Optional<TowerOccupant> findByVisitorKey(String visitorKey);

    long countByInstanceId(Long instanceId);

    /** LUT-632: 미션 중(자리 유지)인 점유자 — 주기 대조용 */
    List<TowerOccupant> findAllByMissionActiveTrue();

    long countByInstanceIdAndKindIn(Long instanceId, Collection<TowerOccupantKind> kinds);

    List<TowerOccupant> findAllByInstanceIdAndKindOrderByEnteredAtDesc(
            Long instanceId, TowerOccupantKind kind);

    /** 층의 인스턴스별 인원 — 정원에 세는 인원(자동 착석 제외)과 전체 */
    @Query(
            "select new"
                + " io.pinkspider.leveluptogethermvp.towerservice.infrastructure.InstanceLoad(o.instanceId,"
                + " sum(case when o.kind = :autoKind then 0L else 1L end), count(o)) from"
                + " TowerOccupant o where o.floorNumber = :floorNumber group by o.instanceId")
    List<InstanceLoad> loadsByFloor(
            @Param("floorNumber") Integer floorNumber,
            @Param("autoKind") TowerOccupantKind autoKind);

    /** 층 전체 인원(모든 종류) */
    @Query(
            "select new"
                + " io.pinkspider.leveluptogethermvp.towerservice.infrastructure.FloorPopulation(o.floorNumber,"
                + " count(o)) from TowerOccupant o group by o.floorNumber")
    List<FloorPopulation> populationByFloor();
}
