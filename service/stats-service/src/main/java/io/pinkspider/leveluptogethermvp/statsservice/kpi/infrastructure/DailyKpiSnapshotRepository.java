package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.DailyKpiSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyKpiSnapshotRepository extends JpaRepository<DailyKpiSnapshot, LocalDate> {

    List<DailyKpiSnapshot> findAllBySnapshotDateBetweenOrderBySnapshotDateAsc(
            LocalDate from, LocalDate to);
}
