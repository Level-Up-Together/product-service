package io.pinkspider.leveluptogethermvp.statsservice.kpi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.KpiRebuildResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("KpiRebuildService 테스트 (LUT-601)")
class KpiRebuildServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @Mock private KpiSnapshotService snapshotService;
    @Mock private CohortRetentionService retentionService;
    @Mock private CohortRevenueService revenueService;

    @InjectMocks private KpiRebuildService service;

    @Test
    @DisplayName("범위 정규화: from>to 는 바꾸고 오늘 이후는 어제로 자른다. 날짜마다 스냅샷, 코호트·매출은 한 번")
    void rebuildNormalizes() {
        when(retentionService.rebuild(
                        LocalDate.of(2026, 10, 20),
                        LocalDate.of(2026, 10, 22),
                        LocalDate.of(2026, 10, 22)))
                .thenReturn(5);
        when(revenueService.rebuild(YearMonth.of(2026, 10), YearMonth.of(2026, 10), TODAY))
                .thenReturn(2);

        KpiRebuildResponse r =
                service.rebuild(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 20), TODAY);

        assertThat(r.from()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(r.to()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(r.snapshotDays()).isEqualTo(3);
        assertThat(r.retentionRows()).isEqualTo(5);
        assertThat(r.revenueRows()).isEqualTo(2);
        verify(snapshotService, times(3)).build(any());
    }

    @Test
    @DisplayName("전부 미래면 아무것도 하지 않고 0, 400일 초과는 끝에서 400일만")
    void edges() {
        KpiRebuildResponse empty = service.rebuild(TODAY, TODAY.plusDays(3), TODAY);
        assertThat(empty.snapshotDays()).isZero();
        verify(snapshotService, never()).build(any());

        KpiRebuildResponse capped = service.rebuild(LocalDate.of(2024, 1, 1), TODAY, TODAY);
        assertThat(capped.snapshotDays()).isEqualTo(KpiRebuildService.MAX_DAYS);
        assertThat(capped.from())
                .isEqualTo(TODAY.minusDays(1).minusDays(KpiRebuildService.MAX_DAYS - 1L));
    }

    @Test
    @DisplayName("runDaily: 어제·그제 스냅샷, 최근 31일 코호트, 전월·당월 매출")
    void runDaily() {
        when(retentionService.rebuild(
                        LocalDate.of(2026, 9, 21),
                        LocalDate.of(2026, 10, 22),
                        LocalDate.of(2026, 10, 22)))
                .thenReturn(7);
        when(revenueService.rebuild(YearMonth.of(2026, 9), YearMonth.of(2026, 10), TODAY))
                .thenReturn(3);

        KpiRebuildResponse r = service.runDaily(TODAY);

        verify(snapshotService).build(LocalDate.of(2026, 10, 21));
        verify(snapshotService).build(LocalDate.of(2026, 10, 22));
        assertThat(r.snapshotDays()).isEqualTo(2);
        assertThat(r.retentionRows()).isEqualTo(7);
        assertThat(r.revenueRows()).isEqualTo(3);
    }

    @Test
    @DisplayName("공개 rebuild(from,to) 는 KST 오늘을 기준으로 한다")
    void publicRebuildUsesToday() {
        KpiRebuildResponse r = service.rebuild(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 2));
        assertThat(r.snapshotDays()).isZero();
    }
}
