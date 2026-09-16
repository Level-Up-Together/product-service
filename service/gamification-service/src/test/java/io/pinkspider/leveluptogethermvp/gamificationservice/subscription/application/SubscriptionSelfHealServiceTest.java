package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.AppleSubscriptionSnapshot;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleSubscriptionState;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * LUT-499: 자가 치유 — 만료됐는데 자동갱신 중인 구독만 스토어를 재조회해 웹훅 경로로 반영한다. 실패는 삼킨다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionSelfHealService 테스트 (LUT-499)")
class SubscriptionSelfHealServiceTest {

    @Mock
    private SubscriptionVerificationService verificationService;

    @Mock
    private SubscriptionWebhookTxService webhookTxService;

    @InjectMocks
    private SubscriptionSelfHealService selfHealService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 10, 0, 0);

    private UserSubscription android(LocalDateTime expiresAt, boolean autoRenew) {
        return UserSubscription.builder()
            .userId("user-1")
            .platform("android")
            .productId("membership")
            .basePlanId("1m")
            .plan(SubscriptionPlan.MONTHLY)
            .startedAt(NOW.minusMonths(1))
            .expiresAt(expiresAt)
            .autoRenew(autoRenew)
            .trialUsed(false)
            .purchaseToken("token-001")
            .build();
    }

    private UserSubscription ios(LocalDateTime expiresAt, boolean autoRenew) {
        return UserSubscription.builder()
            .userId("user-1")
            .platform("ios")
            .productId("membership_1m")
            .plan(SubscriptionPlan.MONTHLY)
            .startedAt(NOW.minusMonths(1))
            .expiresAt(expiresAt)
            .autoRenew(autoRenew)
            .trialUsed(false)
            .originalTransactionId("orig-tx-001")
            .build();
    }

    @Test
    @DisplayName("만료 + 자동갱신 중이면 stale — 아직 유효하거나 자동갱신이 꺼져 있으면 대상이 아니다")
    void isStale() {
        assertThat(selfHealService.isStale(android(NOW.minusMinutes(1), true), NOW)).isTrue();
        assertThat(selfHealService.isStale(android(NOW.plusDays(1), true), NOW)).isFalse();
        assertThat(selfHealService.isStale(android(NOW.minusMinutes(1), false), NOW)).isFalse();
        assertThat(selfHealService.isStale(null, NOW)).isFalse();
        // 유예기간 안이면 아직 권한이 있으므로 대상 아님
        UserSubscription grace = android(NOW.minusDays(1), true);
        grace.enterGracePeriod(NOW.plusDays(3));
        assertThat(selfHealService.isStale(grace, NOW)).isFalse();
    }

    @Test
    @DisplayName("Android: subscriptionsv2 재조회 결과를 웹훅 경로(applyGoogleState)로 반영하고 true")
    void androidStaleSynced() {
        UserSubscription sub = android(NOW.minusMinutes(5), true);
        GoogleSubscriptionState state = new GoogleSubscriptionState(
            "membership", "1m", null, NOW.plusDays(29), true, false, "SUBSCRIPTION_STATE_ACTIVE");
        when(verificationService.fetchGoogleSubscription("token-001")).thenReturn(state);

        assertThat(selfHealService.syncIfStale(sub, NOW)).isTrue();

        verify(webhookTxService).applyGoogleState("token-001", state);
    }

    @Test
    @DisplayName("iOS: Get All Subscription Statuses 스냅샷을 applyAppleSnapshot 으로 반영하고 true")
    void iosStaleSynced() {
        UserSubscription sub = ios(NOW.minusMinutes(5), true);
        AppleSubscriptionSnapshot snapshot = new AppleSubscriptionSnapshot(
            new JWSTransactionDecodedPayload().originalTransactionId("orig-tx-001"), null);
        when(verificationService.fetchAppleLatestSubscription("orig-tx-001")).thenReturn(snapshot);

        assertThat(selfHealService.syncIfStale(sub, NOW)).isTrue();

        verify(webhookTxService).applyAppleSnapshot("orig-tx-001", snapshot);
    }

    @Test
    @DisplayName("대상이 아니면 스토어를 호출하지 않고 false")
    void notStaleSkips() {
        assertThat(selfHealService.syncIfStale(android(NOW.plusDays(1), true), NOW)).isFalse();

        verify(verificationService, never()).fetchGoogleSubscription(any());
        verify(webhookTxService, never()).applyGoogleState(any(), any());
    }

    @Test
    @DisplayName("스토어 재조회 실패는 삼키고 false — 화면은 DB 값으로 뜬다")
    void storeFailureSwallowed() {
        UserSubscription sub = android(NOW.minusMinutes(5), true);
        when(verificationService.fetchGoogleSubscription("token-001"))
            .thenThrow(new CustomException("120702", "error.iap.verification_failed"));

        assertThat(selfHealService.syncIfStale(sub, NOW)).isFalse();

        verify(webhookTxService, never()).applyGoogleState(any(), any());
    }

    @Test
    @DisplayName("식별자(purchaseToken/originalTransactionId)가 없으면 재조회 없이 false")
    void missingIdentifierSkips() {
        UserSubscription sub = android(NOW.minusMinutes(5), true);
        sub.setPurchaseToken(null);

        assertThat(selfHealService.syncIfStale(sub, NOW)).isFalse();
        verify(verificationService, never()).fetchGoogleSubscription(any());
    }
}
