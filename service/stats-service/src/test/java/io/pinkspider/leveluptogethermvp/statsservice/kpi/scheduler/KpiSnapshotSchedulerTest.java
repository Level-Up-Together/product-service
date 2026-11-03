package io.pinkspider.leveluptogethermvp.statsservice.kpi.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.application.KpiRebuildService;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.KpiRebuildResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("KpiSnapshotScheduler 테스트 (LUT-601)")
class KpiSnapshotSchedulerTest {

    @Mock private KpiRebuildService rebuildService;

    @InjectMocks private KpiSnapshotScheduler scheduler;

    @Test
    @DisplayName("runDaily 를 호출하고, 예외는 삼킨다")
    void runs() {
        LocalDate today = LocalDate.of(2026, 10, 23);
        when(rebuildService.runDaily(today))
                .thenReturn(
                        new KpiRebuildResponse(today.minusDays(2), today.minusDays(1), 2, 0, 0));
        scheduler.buildDailySnapshot(today);
        verify(rebuildService).runDaily(today);

        when(rebuildService.runDaily(today.plusDays(1)))
                .thenThrow(new IllegalStateException("db down"));
        scheduler.buildDailySnapshot(today.plusDays(1));
    }

    @Test
    @DisplayName("스케줄 진입점은 KST 오늘로 실행한다")
    void entryPoint() {
        scheduler.buildDailySnapshot();
        verify(rebuildService).runDaily(any(LocalDate.class));
    }
}
