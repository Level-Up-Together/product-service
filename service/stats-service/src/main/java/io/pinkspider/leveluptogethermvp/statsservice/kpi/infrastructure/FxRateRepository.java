package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.FxRate;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.FxRateId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRate, FxRateId> {}
