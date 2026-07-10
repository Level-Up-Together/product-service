package io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserDiamond;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDiamondRepository extends JpaRepository<UserDiamond, Long> {

    Optional<UserDiamond> findByUserId(String userId);
}
