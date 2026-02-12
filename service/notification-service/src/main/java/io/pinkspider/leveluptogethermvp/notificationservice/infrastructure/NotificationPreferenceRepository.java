package io.pinkspider.leveluptogethermvp.notificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.NotificationPreference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserId(String userId);

    boolean existsByUserId(String userId);
}
