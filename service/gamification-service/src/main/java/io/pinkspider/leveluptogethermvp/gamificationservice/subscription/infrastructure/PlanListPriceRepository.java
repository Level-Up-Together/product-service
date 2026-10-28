package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.PlanListPrice;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanListPriceRepository extends JpaRepository<PlanListPrice, Long> {

    /** 결제일 이전에 발효된 가장 최근 정가 */
    Optional<PlanListPrice>
            findTopByPlanAndPlatformAndCurrencyAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                    SubscriptionPlan plan, String platform, String currency, LocalDate onDate);
}
