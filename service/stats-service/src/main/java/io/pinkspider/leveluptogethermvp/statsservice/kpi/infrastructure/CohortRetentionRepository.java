package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRetention;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRetentionId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CohortRetentionRepository
        extends JpaRepository<CohortRetention, CohortRetentionId> {

    List<CohortRetention> findAllByIdCohortDateBetweenOrderByIdCohortDateAscIdMetricAsc(
            LocalDate from, LocalDate to);

    List<CohortRetention> findAllByIdCohortDateBetweenAndIdSourceOrderByIdCohortDateAscIdMetricAsc(
            LocalDate from, LocalDate to, String source);
}
