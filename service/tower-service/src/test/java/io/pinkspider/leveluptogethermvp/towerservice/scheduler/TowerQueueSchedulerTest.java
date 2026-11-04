package io.pinkspider.leveluptogethermvp.towerservice.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.queue.TowerQueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerQueueSchedulerTest {

    @Test
    @DisplayName("LUT-701: 입장 허용 계산을 돌리고, 실패해도 스케줄러는 죽지 않는다")
    void process() {
        TowerQueueService service = mock(TowerQueueService.class);
        TowerQueueScheduler scheduler = new TowerQueueScheduler(service);
        scheduler.process();
        when(service.admitPending()).thenThrow(new IllegalStateException("redis"));
        scheduler.process();
        verify(service, times(2)).admitPending();
    }
}
