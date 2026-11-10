package io.pinkspider.leveluptogethermvp.towerservice.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.TowerSeatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerSeatReconcileSchedulerTest {

    private final TowerSeatService seatService = mock(TowerSeatService.class);
    private final TowerSeatReconcileScheduler scheduler =
            new TowerSeatReconcileScheduler(seatService);

    @Test
    @DisplayName("자리 대조를 호출하고 예외는 삼킨다")
    void reconcile() {
        when(seatService.reconcile()).thenReturn(2);
        scheduler.reconcile();
        verify(seatService).reconcile();

        when(seatService.reconcile()).thenThrow(new RuntimeException("db down"));
        assertThatCode(scheduler::reconcile).doesNotThrowAnyException();
    }
}
