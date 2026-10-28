package io.pinkspider.leveluptogethermvp.statsservice.kpi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CohortRevenueService 테스트 (LUT-601)")
class CohortRevenueServiceTest {

    @Mock private CohortRevenueRepository revenueRepository;
    @Mock private UserSourceReader userReader;
    @Mock private GamificationSourceReader gamificationReader;

    @InjectMocks private CohortRevenueService service;

    @Test
    @DisplayName("월말이 지난 점(month_n)만 저장하고, 기존 행은 갱신한다")
    void rebuild() {
        YearMonth month = YearMonth.of(2026, 8);
        LocalDate today =
                LocalDate.of(2026, 10, 23); // 8월 코호트: n=0(9/1), n=1(10/1) 닫힘, n=3(12/1) 열림
        when(userReader.findSignupUserIds(
                        KpiDates.utcMonthStart(month), KpiDates.utcMonthEnd(month)))
                .thenReturn(List.of("u1", "u2"));
        when(gamificationReader.aggregateCohortRevenueKrw(
                        eq(List.of("u1", "u2")), eq(KpiDates.utcStart(LocalDate.of(2026, 9, 1)))))
                .thenReturn(
                        new RevenueAggregate(
                                Map.of("KRW", new BigDecimal("4900.00")), Set.of("u1")));
        when(gamificationReader.aggregateCohortRevenueKrw(
                        eq(List.of("u1", "u2")), eq(KpiDates.utcStart(LocalDate.of(2026, 10, 1)))))
                .thenReturn(
                        new RevenueAggregate(
                                Map.of("KRW", new BigDecimal("9800.00")), Set.of("u1")));
        CohortRevenue existing = CohortRevenue.create("2026-08", 0, 0, 0, BigDecimal.ZERO);
        when(revenueRepository.findById(new CohortRevenueId("2026-08", 0)))
                .thenReturn(Optional.of(existing));
        when(revenueRepository.findById(new CohortRevenueId("2026-08", 1)))
                .thenReturn(Optional.empty());
        when(revenueRepository.save(any(CohortRevenue.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int rows = service.rebuild(month, month, today);

        assertThat(rows).isEqualTo(2);
        assertThat(existing.getCohortSize()).isEqualTo(2);
        assertThat(existing.getPayingUsers()).isEqualTo(1);
        assertThat(existing.getCumulativeRevenueKrw()).isEqualByComparingTo("4900.00");
        ArgumentCaptor<CohortRevenue> captor = ArgumentCaptor.forClass(CohortRevenue.class);
        verify(revenueRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        CohortRevenue created = captor.getAllValues().get(1);
        assertThat(created.getId().getMonthN()).isEqualTo(1);
        assertThat(created.getCumulativeRevenueKrw()).isEqualByComparingTo("9800.00");
    }

    @Test
    @DisplayName("from>to 면 0, 코호트가 비어도 닫힌 점은 0 으로 저장한다")
    void edges() {
        assertThat(
                        service.rebuild(
                                YearMonth.of(2026, 10),
                                YearMonth.of(2026, 9),
                                LocalDate.of(2026, 10, 23)))
                .isZero();

        YearMonth month = YearMonth.of(2026, 9);
        when(userReader.findSignupUserIds(any(), any())).thenReturn(List.of());
        when(gamificationReader.aggregateCohortRevenueKrw(eq(List.of()), any()))
                .thenReturn(RevenueAggregate.empty());
        when(revenueRepository.findById(any(CohortRevenueId.class))).thenReturn(Optional.empty());
        when(revenueRepository.save(any(CohortRevenue.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int rows =
                service.rebuild(month, month, LocalDate.of(2026, 10, 23)); // n=0 만 닫힘(10/1 ≤ 10/23)

        assertThat(rows).isEqualTo(1);
    }
}
