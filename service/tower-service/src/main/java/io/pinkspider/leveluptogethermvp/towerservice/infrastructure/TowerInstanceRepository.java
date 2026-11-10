package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerInstance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TowerInstanceRepository extends JpaRepository<TowerInstance, Long> {

    List<TowerInstance> findAllByFloorNumberOrderByIdAsc(Integer floorNumber);
}
