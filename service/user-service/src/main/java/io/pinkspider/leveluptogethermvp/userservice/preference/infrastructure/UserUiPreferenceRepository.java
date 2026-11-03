package io.pinkspider.leveluptogethermvp.userservice.preference.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.preference.domain.entity.UserUiPreference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserUiPreferenceRepository extends JpaRepository<UserUiPreference, Long> {

    Optional<UserUiPreference> findByUserId(String userId);
}
