package io.pinkspider.leveluptogethermvp.statsservice.kpi.application;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source.RevenueAggregate;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRevenue;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRevenueId;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.CohortRevenueRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.GamificationSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.UserSourceReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-601: 가입 월 코호트의 누적 KRW 매출 (LTV 곡선). month_n ∈ {0,1,3,6,12} — 가입 월부터 n개월 뒤 월말까지의 결제 합. 그 월말이 아직
 * 지나지 않은 점은 저장하지 않는다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CohortRevenueService {

    static final int[] MONTH_POINTS = {0, 1, 3, 6, 12};

    private final CohortRevenueRepository revenueRepository;
    private final UserSourceReader userReader;
    private final GamificationSourceReader gamificationReader;

    /**
     * @param today 오늘(KST) — 아직 끝나지 않은 월은 건너뛴다
     * @return 저장한 행 수
     */
    @Transactional(transactionManager = "statsTransactionManager")
    public int rebuild(YearMonth fromMonth, YearMonth toMonth, LocalDate today) {
        if (fromMonth.isAfter(toMonth)) {
            return 0;
        }
        int written = 0;
        for (YearMonth month = fromMonth; !month.isAfter(toMonth); month = month.plusMonths(1)) {
            List<String> members =
                    userReader.findSignupUserIds(
                            KpiDates.utcMonthStart(month), KpiDates.utcMonthEnd(month));
            final String monthKey = KpiDates.monthKey(month);
            for (int n : MONTH_POINTS) {
                final int point = n;
                LocalDate cutoffKst = month.plusMonths(n + 1L).atDay(1);
                if (cutoffKst.isAfter(today)) {
                    continue; // 이 점의 창(해당 월말)이 아직 안 지남
                }
                RevenueAggregate revenue =
                        gamificationReader.aggregateCohortRevenueKrw(
                                members, KpiDates.utcStart(cutoffKst));
                BigDecimal krw = revenue.amountOf(KpiSnapshotService.KRW);
                int paying = revenue.payingUserIds().size();
                CohortRevenueId id = new CohortRevenueId(monthKey, point);
                CohortRevenue row =
                        revenueRepository
                                .findById(id)
                                .map(
                                        existing -> {
                                            existing.update(members.size(), paying, krw);
                                            return existing;
                                        })
                                .orElseGet(
                                        () ->
                                                CohortRevenue.create(
                                                        monthKey,
                                                        point,
                                                        members.size(),
                                                        paying,
                                                        krw));
                revenueRepository.save(row);
                written++;
            }
        }
        log.info("코호트 매출 재계산: {}~{}, rows={}", fromMonth, toMonth, written);
        return written;
    }
}
