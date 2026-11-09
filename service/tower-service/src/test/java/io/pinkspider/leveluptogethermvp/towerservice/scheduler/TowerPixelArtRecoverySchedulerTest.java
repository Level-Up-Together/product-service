package io.pinkspider.leveluptogethermvp.towerservice.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.pixel.TowerPixelArtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerPixelArtRecoverySchedulerTest {

    @Test
    @DisplayName("LUT-727: 대기 복구를 부르고, 실패는 로그만 남긴다")
    void recover() {
        TowerPixelArtService service = mock(TowerPixelArtService.class);
        TowerPixelArtRecoveryScheduler scheduler = new TowerPixelArtRecoveryScheduler(service);
        scheduler.recover();
        verify(service).recoverStale();
        when(service.recoverStale()).thenThrow(new IllegalStateException("db down"));
        assertThatCode(scheduler::recover).doesNotThrowAnyException();
    }
}
