package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TowerFloorRepository extends JpaRepository<TowerFloor, Long> {

    List<TowerFloor> findAllByOrderByFloorNumberAsc();

    List<TowerFloor> findAllByIsPublicTrueOrderByFloorNumberAsc();

    Optional<TowerFloor> findByFloorNumber(Integer floorNumber);
}
