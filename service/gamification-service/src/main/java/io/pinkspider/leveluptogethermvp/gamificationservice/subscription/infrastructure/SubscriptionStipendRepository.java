package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionStipend;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionStipendRepository extends JpaRepository<SubscriptionStipend, Long> {

    boolean existsBySubscriptionIdAndStipendDate(Long subscriptionId, LocalDate stipendDate);
}
