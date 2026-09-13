package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionPaymentHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionPaymentHistoryRecorder 테스트 (LUT-486)")
class SubscriptionPaymentHistoryRecorderTest {

    @Mock
    private SubscriptionPaymentHistoryRepository repository;

    @InjectMocks
    private SubscriptionPaymentHistoryRecorder recorder;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0, 0);

    private UserSubscription subscription() {
        return UserSubscription.builder()
            .userId("user-1")
            .platform("ios")
            .productId("membership_1m")
            .plan(SubscriptionPlan.MONTHLY)
            .startedAt(NOW.minusMonths(1))
            .expiresAt(NOW.plusMonths(1))
            .autoRenew(true)
            .trialUsed(false)
            .build();
    }

    @Test
    @DisplayName("구독 행 스냅샷 + 결제 정보로 이력을 저장한다")
    void recordSavesHistory() {
        when(repository.existsByUserIdAndEventTypeAndExpiresAt(
            "user-1", SubscriptionPaymentEventType.PURCHASE, NOW.plusMonths(1)))
            .thenReturn(false);

        recorder.record(
            subscription(), SubscriptionPaymentEventType.PURCHASE, true,
            new BigDecimal("4900.00"), "KRW", "tx-1001", NOW.plusMonths(1), NOW);

        ArgumentCaptor<SubscriptionPaymentHistory> captor =
            ArgumentCaptor.forClass(SubscriptionPaymentHistory.class);
        verify(repository).save(captor.capture());
        SubscriptionPaymentHistory saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getPlatform()).isEqualTo("ios");
        assertThat(saved.getProductId()).isEqualTo("membership_1m");
        assertThat(saved.getPlan()).isEqualTo(SubscriptionPlan.MONTHLY);
        assertThat(saved.getEventType()).isEqualTo(SubscriptionPaymentEventType.PURCHASE);
        assertThat(saved.getTrial()).isTrue();
        assertThat(saved.getPriceAmount()).isEqualByComparingTo(new BigDecimal("4900.00"));
        assertThat(saved.getPriceCurrency()).isEqualTo("KRW");
        assertThat(saved.getTransactionId()).isEqualTo("tx-1001");
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusMonths(1));
        assertThat(saved.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("같은 (유저, 이벤트, 만료) 이력이 이미 있으면 저장하지 않는다 — verify·웹훅 이중 도착 방어")
    void recordSkipsDuplicate() {
        when(repository.existsByUserIdAndEventTypeAndExpiresAt(
            "user-1", SubscriptionPaymentEventType.RENEWAL, NOW.plusMonths(1)))
            .thenReturn(true);

        recorder.record(
            subscription(), SubscriptionPaymentEventType.RENEWAL, false,
            null, null, null, NOW.plusMonths(1), NOW);

        verify(repository, never()).save(any());
    }
}
