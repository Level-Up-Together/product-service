package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionLedgerReconcileService;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.DailyLedgerReconcileSummary;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionLedgerReconcileScheduler 테스트 (LUT-677)")
class SubscriptionLedgerReconcileSchedulerTest {

    @Mock private SubscriptionLedgerReconcileService reconcileService;

    @InjectMocks private SubscriptionLedgerReconcileScheduler scheduler;

    @Test
    @DisplayName("대상을 한 명씩 대조하고, 한 명의 실패는 건너뛴 뒤 나머지를 계속한다")
    void reconcilesEachTargetAndSkipsFailures() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 0, 30);
        when(reconcileService.findAndroidDailyTargets(now)).thenReturn(List.of("u1", "u2", "u3"));
        when(reconcileService.reconcileGoogleIncremental("u1")).thenReturn(2);
        when(reconcileService.reconcileGoogleIncremental("u2"))
                .thenThrow(new CustomException("120702", "error.iap.verification_failed"));
        when(reconcileService.reconcileGoogleIncremental("u3")).thenReturn(0);

        DailyLedgerReconcileSummary summary = scheduler.run(now);

        assertThat(summary).isEqualTo(new DailyLedgerReconcileSummary(3, 2, 1, 2));
        verify(reconcileService).reconcileGoogleIncremental("u3");
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 하지 않고 0 요약을 돌려준다")
    void noTargets() {
        when(reconcileService.findAndroidDailyTargets(any())).thenReturn(List.of());

        assertThat(scheduler.run(LocalDateTime.now()))
                .isEqualTo(new DailyLedgerReconcileSummary(0, 0, 0, 0));
    }

    @Test
    @DisplayName("스케줄 진입점은 예외를 전파하지 않는다 (ShedLock 락 해제 보장)")
    void scheduledEntryPointSwallowsErrors() {
        when(reconcileService.findAndroidDailyTargets(any()))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> scheduler.reconcileAndroidLedgers()).doesNotThrowAnyException();
    }
}
