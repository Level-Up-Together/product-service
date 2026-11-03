package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRevenue;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRevenueId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CohortRevenueRepository extends JpaRepository<CohortRevenue, CohortRevenueId> {

    List<CohortRevenue> findAllByIdCohortMonthBetweenOrderByIdCohortMonthAscIdMonthNAsc(
            String fromMonth, String toMonth);
}
