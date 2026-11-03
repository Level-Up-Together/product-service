package io.pinkspider.leveluptogethermvp.statsservice.activity.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.pinkspider.leveluptogethermvp.statsservice.activity.application.UserActivityFlushService;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserActivityFlushScheduler 테스트 (LUT-598)")
class UserActivityFlushSchedulerTest {

    @Mock private UserActivityFlushService flushService;

    @InjectMocks private UserActivityFlushScheduler scheduler;

    @Test
    @DisplayName("그제 → 어제 순서로 2일을 flush 한다 (전날 실행 누락 보정)")
    void flushesTwoDaysOldestFirst() {
        LocalDate today = LocalDate.of(2026, 10, 23);

        scheduler.flushRecentDays(today);

        InOrder order = inOrder(flushService);
        order.verify(flushService).flush(LocalDate.of(2026, 10, 21));
        order.verify(flushService).flush(LocalDate.of(2026, 10, 22));
        verify(flushService, times(2)).flush(any());
    }

    @Test
    @DisplayName("한 날짜가 실패해도 다음 날짜는 계속 flush 한다")
    void continuesAfterFailure() {
        LocalDate today = LocalDate.of(2026, 10, 23);
        doThrow(new IllegalStateException("redis down"))
                .when(flushService)
                .flush(LocalDate.of(2026, 10, 21));

        scheduler.flushRecentDays(today);

        verify(flushService).flush(LocalDate.of(2026, 10, 22));
    }

    @Test
    @DisplayName("스케줄 진입점은 KST 오늘 기준으로 2일을 flush 한다")
    void entryPointUsesToday() {
        scheduler.flushRecentDays();

        verify(flushService, times(2)).flush(any());
    }
}
