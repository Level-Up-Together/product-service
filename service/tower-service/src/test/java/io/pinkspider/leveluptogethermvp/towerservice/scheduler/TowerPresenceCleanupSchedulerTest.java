package io.pinkspider.leveluptogethermvp.towerservice.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerConnectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerPresenceCleanupSchedulerTest {

    private final TowerConnectionService connectionService = mock(TowerConnectionService.class);
    private final TowerPresenceCleanupScheduler scheduler =
            new TowerPresenceCleanupScheduler(connectionService);

    @Test
    @DisplayName("정리를 호출하고 예외는 삼킨다")
    void cleanup() {
        when(connectionService.cleanup()).thenReturn(1);
        scheduler.cleanup();
        verify(connectionService).cleanup();

        when(connectionService.cleanup()).thenThrow(new RuntimeException("down"));
        assertThatCode(scheduler::cleanup).doesNotThrowAnyException();
    }
}
