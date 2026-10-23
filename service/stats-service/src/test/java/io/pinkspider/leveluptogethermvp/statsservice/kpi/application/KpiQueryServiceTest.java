package io.pinkspider.leveluptogethermvp.statsservice.kpi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.CohortRetentionResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.CohortRevenueResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.DailyKpiSnapshotResponse;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.DailyKpiValues;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRetention;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRevenue;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.DailyKpiSnapshot;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionMetric;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionSource;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.CohortRetentionRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.CohortRevenueRepository;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.DailyKpiSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("KpiQueryService 테스트 (LUT-601)")
class KpiQueryServiceTest {

    @Mock private DailyKpiSnapshotRepository snapshotRepository;
    @Mock private CohortRetentionRepository retentionRepository;
    @Mock private CohortRevenueRepository revenueRepository;

    @InjectMocks private KpiQueryService service;

    @Test
    @DisplayName("daily: from>to 를 바꿔 조회하고 응답으로 변환")
    void daily() {
        DailyKpiValues v =
                new DailyKpiValues(
                        1,
                        0,
                        10,
                        5,
                        8,
                        12,
                        3,
                        2,
                        1,
                        4,
                        6,
                        3,
                        1,
                        2,
                        0,
                        1,
                        0,
                        1,
                        new BigDecimal("4900.00"),
                        "{\"KRW\":4900.00}");
        when(snapshotRepository.findAllBySnapshotDateBetweenOrderBySnapshotDateAsc(
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3)))
                .thenReturn(List.of(DailyKpiSnapshot.create(LocalDate.of(2026, 10, 1), v)));

        List<DailyKpiSnapshotResponse> rows =
                service.daily(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).dau()).isEqualTo(5);
        assertThat(rows.get(0).revenueByCurrency()).isEqualTo("{\"KRW\":4900.00}");
    }

    @Test
    @DisplayName("retention: source 없으면 전체, 있으면 그 원천만. rate 는 소수 4자리, 분모 0 이면 null")
    void retention() {
        LocalDate c = LocalDate.of(2026, 10, 1);
        when(retentionRepository.findAllByIdCohortDateBetweenOrderByIdCohortDateAscIdMetricAsc(
                        c, c))
                .thenReturn(
                        List.of(
                                CohortRetention.create(
                                        c, RetentionMetric.D1, RetentionSource.ATTENDANCE, 3, 1)));
        when(retentionRepository
                        .findAllByIdCohortDateBetweenAndIdSourceOrderByIdCohortDateAscIdMetricAsc(
                                c, c, "REQUEST"))
                .thenReturn(
                        List.of(
                                CohortRetention.create(
                                        c, RetentionMetric.D1, RetentionSource.REQUEST, 0, 0)));

        List<CohortRetentionResponse> all = service.retention(c, c, null);
        List<CohortRetentionResponse> request = service.retention(c, c, RetentionSource.REQUEST);

        assertThat(all.get(0).rate()).isEqualByComparingTo("0.3333");
        assertThat(request.get(0).rate()).isNull();
        assertThat(request.get(0).source()).isEqualTo("REQUEST");
    }

    @Test
    @DisplayName("revenueCohorts: 월 범위 조회, ltv = 누적 ÷ 코호트 크기")
    void revenueCohorts() {
        when(revenueRepository.findAllByIdCohortMonthBetweenOrderByIdCohortMonthAscIdMonthNAsc(
                        "2026-08", "2026-10"))
                .thenReturn(
                        List.of(
                                CohortRevenue.create("2026-08", 1, 4, 1, new BigDecimal("9800.00")),
                                CohortRevenue.create("2026-09", 0, 0, 0, BigDecimal.ZERO)));

        List<CohortRevenueResponse> rows =
                service.revenueCohorts(YearMonth.of(2026, 10), YearMonth.of(2026, 8));

        assertThat(rows.get(0).ltvKrw()).isEqualByComparingTo("2450.00");
        assertThat(rows.get(1).ltvKrw()).isNull();
    }
}
