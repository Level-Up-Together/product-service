package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerUserState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TowerUserStateRepository extends JpaRepository<TowerUserState, String> {}
