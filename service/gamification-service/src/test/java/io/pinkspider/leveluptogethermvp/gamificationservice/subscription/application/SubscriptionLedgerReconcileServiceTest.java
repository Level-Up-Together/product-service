package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleSubscriptionState;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionLedgerReconcileResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionPaymentHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.UserSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionLedgerReconcileService 테스트 (LUT-617)")
class SubscriptionLedgerReconcileServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 4, 0);

    @Mock private UserSubscriptionRepository userSubscriptionRepository;
    @Mock private SubscriptionPaymentHistoryRepository historyRepository;
    @Mock private SubscriptionVerificationService verificationService;
    @Mock private GoogleOrderLedgerSync ledgerSync;

    @InjectMocks private SubscriptionLedgerReconcileService service;

    private UserSubscription row(String platform, String purchaseToken) {
        return UserSubscription.builder()
                .userId("user-1")
                .platform(platform)
                .productId("membership")
                .basePlanId("1m")
                .plan(SubscriptionPlan.MONTHLY)
                .startedAt(NOW.minusHours(1))
                .expiresAt(NOW.minusMinutes(13))
                .autoRenew(false)
                .trialUsed(false)
                .purchaseToken(purchaseToken)
                .build();
    }

    @Test
    @DisplayName("Android 구독을 Play 재조회 후 1번부터 전부 대조(full)하고 전후 행 수를 돌려준다")
    void reconcilesAndroid() {
        UserSubscription sub = row("android", "token-001");
        when(userSubscriptionRepository.findByUserId("user-1")).thenReturn(Optional.of(sub));
        GoogleSubscriptionState state =
                new GoogleSubscriptionState(
                        "membership",
                        "1m",
                        NOW.minusHours(1),
                        NOW.minusMinutes(13),
                        false,
                        false,
                        "SUBSCRIPTION_STATE_EXPIRED",
                        null,
                        "GPA.3329-9857-6705-37595..5");
        when(verificationService.fetchGoogleSubscription("token-001")).thenReturn(state);
        when(historyRepository.countByUserId("user-1")).thenReturn(3L, 6L);
        when(ledgerSync.syncRenewals(any(), any(), anyBoolean(), any(), any(), anyBoolean()))
                .thenReturn(4);

        SubscriptionLedgerReconcileResponse response = service.reconcileGoogle("user-1");

        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.latestOrderId()).isEqualTo("GPA.3329-9857-6705-37595..5");
        assertThat(response.subscriptionState()).isEqualTo("SUBSCRIPTION_STATE_EXPIRED");
        assertThat(response.rowsBefore()).isEqualTo(3L);
        assertThat(response.rowsAfter()).isEqualTo(6L);
        assertThat(response.rowsWritten()).isEqualTo(4);
        verify(ledgerSync)
                .syncRenewals(
                        eq(sub),
                        eq("GPA.3329-9857-6705-37595..5"),
                        eq(false),
                        eq(NOW.minusMinutes(13)),
                        isNull(),
                        eq(true));
        // 구독 행은 건드리지 않는다
        assertThat(sub.getExpiresAt()).isEqualTo(NOW.minusMinutes(13));
        assertThat(sub.getTestPurchase()).isFalse();
    }

    @Test
    @DisplayName("LUT-666: Play 재조회가 라이선스 테스터 결제면 구독 행을 테스트 결제로 맞춘 뒤 대조한다")
    void marksTestPurchaseBeforeSync() {
        UserSubscription sub = row("android", "token-001");
        when(userSubscriptionRepository.findByUserId("user-1")).thenReturn(Optional.of(sub));
        when(verificationService.fetchGoogleSubscription("token-001"))
                .thenReturn(
                        new GoogleSubscriptionState(
                                "membership",
                                "1m",
                                NOW.minusHours(1),
                                NOW.minusMinutes(13),
                                false,
                                false,
                                "SUBSCRIPTION_STATE_EXPIRED",
                                null,
                                "GPA.1..5",
                                null,
                                true));
        when(historyRepository.countByUserId("user-1")).thenReturn(3L, 3L);

        service.reconcileGoogle("user-1");

        assertThat(sub.getTestPurchase()).isTrue();
    }

    @Test
    @DisplayName("Play 응답에 만료가 없으면 구독 행의 만료를 쓴다")
    void fallsBackToRowExpiry() {
        UserSubscription sub = row("android", "token-001");
        when(userSubscriptionRepository.findByUserId("user-1")).thenReturn(Optional.of(sub));
        when(verificationService.fetchGoogleSubscription("token-001"))
                .thenReturn(
                        new GoogleSubscriptionState(
                                null,
                                null,
                                null,
                                null,
                                false,
                                false,
                                "SUBSCRIPTION_STATE_PENDING"));

        service.reconcileGoogle("user-1");

        verify(ledgerSync)
                .syncRenewals(
                        eq(sub), isNull(), eq(false), eq(NOW.minusMinutes(13)), isNull(), eq(true));
    }

    @Test
    @DisplayName("구독 행이 없으면 120803")
    void notFound() {
        when(userSubscriptionRepository.findByUserId("user-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reconcileGoogle("user-x"))
                .isInstanceOf(CustomException.class)
                .extracting("code")
                .isEqualTo("120803");
        verify(ledgerSync, never())
                .syncRenewals(any(), any(), anyBoolean(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("iOS 구독·토큰 없는 Android 는 120804")
    void unsupportedPlatform() {
        when(userSubscriptionRepository.findByUserId("user-1"))
                .thenReturn(Optional.of(row("ios", null)));
        assertThatThrownBy(() -> service.reconcileGoogle("user-1"))
                .isInstanceOf(CustomException.class)
                .extracting("code")
                .isEqualTo("120804");

        when(userSubscriptionRepository.findByUserId("user-2"))
                .thenReturn(Optional.of(row("android", null)));
        assertThatThrownBy(() -> service.reconcileGoogle("user-2"))
                .isInstanceOf(CustomException.class)
                .extracting("code")
                .isEqualTo("120804");
    }

    @Test
    @DisplayName("Play 재조회 일시 실패는 내부 API 응답 계약대로 120702")
    void transientBecomesCustomException() {
        when(userSubscriptionRepository.findByUserId("user-1"))
                .thenReturn(Optional.of(row("android", "token-001")));
        when(verificationService.fetchGoogleSubscription("token-001"))
                .thenThrow(new StoreTransientException("503", null));

        assertThatThrownBy(() -> service.reconcileGoogle("user-1"))
                .isInstanceOf(CustomException.class)
                .extracting("code")
                .isEqualTo("120702");
    }
}
