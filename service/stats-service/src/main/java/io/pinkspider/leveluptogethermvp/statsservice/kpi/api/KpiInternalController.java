package io.pinkspider.leveluptogethermvp.statsservice.kpi.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.application.KpiQueryService;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.application.KpiRebuildService;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.CohortRetentionResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.CohortRevenueResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.DailyKpiSnapshotResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.KpiRebuildResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionSource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-601: KPI 내부 API (admin-service Feign · 운영 점검, {@code X-Internal-Api-Key}).
 *
 * <ul>
 *   <li>{@code POST /api/internal/kpi/rebuild?from&to} — 스냅샷·코호트 재계산(소급). 어제까지로 잘리고 최대 400일
 *   <li>{@code GET /api/internal/kpi/daily?from&to}
 *   <li>{@code GET /api/internal/kpi/retention?cohort_from&cohort_to[&source]}
 *   <li>{@code GET /api/internal/kpi/revenue/cohorts?from_month&to_month} (yyyy-MM)
 * </ul>
 */
@RestController
@RequestMapping("/api/internal/kpi")
@RequiredArgsConstructor
public class KpiInternalController {

    private final KpiRebuildService rebuildService;
    private final KpiQueryService queryService;

    @PostMapping("/rebuild")
    public ResponseEntity<ApiResult<KpiRebuildResponse>> rebuild(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(rebuildService.rebuild(from, to));
    }

    @GetMapping("/daily")
    public ResponseEntity<ApiResult<List<DailyKpiSnapshotResponse>>> daily(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(queryService.daily(from, to));
    }

    @GetMapping("/retention")
    public ResponseEntity<ApiResult<List<CohortRetentionResponse>>> retention(
            @RequestParam("cohort_from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate cohortFrom,
            @RequestParam("cohort_to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate cohortTo,
            @RequestParam(value = "source", required = false) RetentionSource source) {
        return ok(queryService.retention(cohortFrom, cohortTo, source));
    }

    @GetMapping("/revenue/cohorts")
    public ResponseEntity<ApiResult<List<CohortRevenueResponse>>> revenueCohorts(
            @RequestParam("from_month") @DateTimeFormat(pattern = "yyyy-MM") YearMonth fromMonth,
            @RequestParam("to_month") @DateTimeFormat(pattern = "yyyy-MM") YearMonth toMonth) {
        return ok(queryService.revenueCohorts(fromMonth, toMonth));
    }

    private static <T> ResponseEntity<ApiResult<T>> ok(T value) {
        return ResponseEntity.ok(ApiResult.<T>builder().value(value).build());
    }
}
