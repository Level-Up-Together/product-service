package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorGuide;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TowerFloorGuideRepository extends JpaRepository<TowerFloorGuide, Long> {

    Optional<TowerFloorGuide> findByFloorId(Long floorId);

    List<TowerFloorGuide> findAllByFloorIdIn(Collection<Long> floorIds);
}
