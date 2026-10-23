package io.pinkspider.leveluptogethermvp.statsservice.kpi.application;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.KpiRebuildResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionMetric;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LUT-601: 스냅샷·코호트 재계산 묶음. 스케줄러(매일)와 내부 rebuild API(소급)가 같은 경로를 쓴다.
 *
 * <p>범위 정규화: from>to 면 바꾸고, 어제(lastComplete)를 넘는 날은 자르며, 한 번에 최대 {@value #MAX_DAYS}일. 날짜별 스냅샷은 각자
 * 트랜잭션이라 중간 실패가 앞선 날을 되돌리지 않는다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KpiRebuildService {

    static final int MAX_DAYS = 400;

    private final KpiSnapshotService snapshotService;
    private final CohortRetentionService retentionService;
    private final CohortRevenueService revenueService;

    public KpiRebuildResponse rebuild(LocalDate from, LocalDate to) {
        return rebuild(from, to, KpiDates.today());
    }

    KpiRebuildResponse rebuild(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate lastComplete = today.minusDays(1);
        LocalDate start = from.isAfter(to) ? to : from;
        LocalDate end = from.isAfter(to) ? from : to;
        if (end.isAfter(lastComplete)) {
            end = lastComplete;
        }
        if (start.isAfter(end)) {
            return new KpiRebuildResponse(start, end, 0, 0, 0);
        }
        if (start.plusDays(MAX_DAYS - 1L).isBefore(end)) {
            start = end.minusDays(MAX_DAYS - 1L);
        }
        int days = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            snapshotService.build(d);
            days++;
        }
        int retentionRows = retentionService.rebuild(start, end, lastComplete);
        int revenueRows = revenueService.rebuild(YearMonth.from(start), YearMonth.from(end), today);
        log.info(
                "KPI rebuild: {}~{} days={} retention={} revenue={}",
                start,
                end,
                days,
                retentionRows,
                revenueRows);
        return new KpiRebuildResponse(start, end, days, retentionRows, revenueRows);
    }

    /** 스케줄러용: 어제·그제 스냅샷 + 창이 닫히는 코호트(최근 31일) + 전월·당월 코호트 매출 */
    public KpiRebuildResponse runDaily(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        snapshotService.build(yesterday.minusDays(1));
        snapshotService.build(yesterday);
        LocalDate cohortFrom = yesterday.minusDays(RetentionMetric.MAX_OFFSET_DAYS + 1L);
        int retentionRows = retentionService.rebuild(cohortFrom, yesterday, yesterday);
        YearMonth thisMonth = YearMonth.from(today);
        int revenueRows = revenueService.rebuild(thisMonth.minusMonths(1), thisMonth, today);
        return new KpiRebuildResponse(
                yesterday.minusDays(1), yesterday, 2, retentionRows, revenueRows);
    }
}
