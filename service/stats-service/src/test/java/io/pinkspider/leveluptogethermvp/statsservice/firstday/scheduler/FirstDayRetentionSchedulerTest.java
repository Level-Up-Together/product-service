package io.pinkspider.leveluptogethermvp.statsservice.firstday.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.firstday.application.FirstDayRetentionService;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto.FirstDayRetentionRunResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FirstDayRetentionScheduler (LUT-779)")
class FirstDayRetentionSchedulerTest {

    @Mock FirstDayRetentionService retentionService;
    @InjectMocks FirstDayRetentionScheduler scheduler;

    @Test
    @DisplayName("오늘·지금으로 집계한다")
    void runs() {
        when(retentionService.runDaily(any(), any()))
                .thenReturn(new FirstDayRetentionRunResponse(LocalDate.of(2026, 10, 1), 3, 5));

        scheduler.run();

        verify(retentionService).runDaily(any(LocalDate.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("실패해도 예외를 밖으로 던지지 않는다(다음 날 다시 돈다)")
    void swallowsFailure() {
        LocalDate today = LocalDate.of(2026, 11, 11);
        LocalDateTime now = LocalDateTime.of(2026, 11, 10, 15, 50);
        when(retentionService.runDaily(today, now)).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> scheduler.run(today, now)).doesNotThrowAnyException();
    }
}
