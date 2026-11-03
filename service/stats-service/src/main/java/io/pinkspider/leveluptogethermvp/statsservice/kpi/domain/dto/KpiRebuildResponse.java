package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto;

import java.time.LocalDate;

/** LUT-601: rebuild 결과 — 실제 처리된 범위(정규화·클램프 후)와 행 수 */
public record KpiRebuildResponse(
        LocalDate from, LocalDate to, int snapshotDays, int retentionRows, int revenueRows) {}
