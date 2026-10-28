package io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.DailyKpiSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyKpiSnapshotRepository extends JpaRepository<DailyKpiSnapshot, LocalDate> {

    List<DailyKpiSnapshot> findAllBySnapshotDateBetweenOrderBySnapshotDateAsc(
            LocalDate from, LocalDate to);

    /** LUT-605: 가장 최근 스냅샷 1행 — Micrometer 게이지(KpiMetricsExporter) 가 읽는다. */
    Optional<DailyKpiSnapshot> findTopByOrderBySnapshotDateDesc();
}
