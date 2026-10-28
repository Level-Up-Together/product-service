package io.pinkspider.leveluptogethermvp.statsservice.kpi.application;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.CohortRetentionResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.CohortRevenueResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.DailyKpiSnapshotResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.SignupsByChannel;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionSource;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.CohortRetentionRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.CohortRevenueRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.DailyKpiSnapshotRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.UserSourceReader;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-601: 스냅샷 조회 — 어드민(P3-a Feign)과 운영 점검이 읽는다. from>to 는 바꿔서 처리. 채널별 가입자(LUT-602)는 user_db 직접 집계
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "statsTransactionManager", readOnly = true)
public class KpiQueryService {

    private final DailyKpiSnapshotRepository snapshotRepository;
    private final CohortRetentionRepository retentionRepository;
    private final CohortRevenueRepository revenueRepository;
    private final UserSourceReader userReader;

    public List<DailyKpiSnapshotResponse> daily(LocalDate from, LocalDate to) {
        LocalDate start = from.isAfter(to) ? to : from;
        LocalDate end = from.isAfter(to) ? from : to;
        return snapshotRepository
                .findAllBySnapshotDateBetweenOrderBySnapshotDateAsc(start, end)
                .stream()
                .map(DailyKpiSnapshotResponse::from)
                .toList();
    }

    public List<CohortRetentionResponse> retention(
            LocalDate cohortFrom, LocalDate cohortTo, RetentionSource source) {
        LocalDate start = cohortFrom.isAfter(cohortTo) ? cohortTo : cohortFrom;
        LocalDate end = cohortFrom.isAfter(cohortTo) ? cohortFrom : cohortTo;
        var rows =
                source == null
                        ? retentionRepository
                                .findAllByIdCohortDateBetweenOrderByIdCohortDateAscIdMetricAsc(
                                        start, end)
                        : retentionRepository
                                .findAllByIdCohortDateBetweenAndIdSourceOrderByIdCohortDateAscIdMetricAsc(
                                        start, end, source.name());
        return rows.stream().map(CohortRetentionResponse::from).toList();
    }

    /** LUT-602: 월 × 채널 가입자 (user_db 직접 집계, 스냅샷 아님) */
    public List<SignupsByChannel> signupsByChannel(YearMonth fromMonth, YearMonth toMonth) {
        YearMonth start = fromMonth.isAfter(toMonth) ? toMonth : fromMonth;
        YearMonth end = fromMonth.isAfter(toMonth) ? fromMonth : toMonth;
        return userReader.countSignupsByChannel(
                KpiDates.utcMonthStart(start), KpiDates.utcMonthEnd(end));
    }

    public List<CohortRevenueResponse> revenueCohorts(YearMonth fromMonth, YearMonth toMonth) {
        YearMonth start = fromMonth.isAfter(toMonth) ? toMonth : fromMonth;
        YearMonth end = fromMonth.isAfter(toMonth) ? fromMonth : toMonth;
        return revenueRepository
                .findAllByIdCohortMonthBetweenOrderByIdCohortMonthAscIdMonthNAsc(
                        KpiDates.monthKey(start), KpiDates.monthKey(end))
                .stream()
                .map(CohortRevenueResponse::from)
                .toList();
    }
}
