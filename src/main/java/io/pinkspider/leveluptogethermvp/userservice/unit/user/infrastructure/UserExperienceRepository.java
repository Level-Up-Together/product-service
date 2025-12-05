package io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserExperience;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserExperienceRepository extends JpaRepository<UserExperience, Long> {

    Optional<UserExperience> findByUserId(String userId);

    boolean existsByUserId(String userId);
}
