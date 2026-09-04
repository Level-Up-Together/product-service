package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import com.apple.itunes.storekit.model.AutoRenewStatus;
import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.AppleSubscriptionNotification;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleSubscriptionState;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionWebhookTxService 테스트 (LUT-452)")
class SubscriptionWebhookTxServiceTest {

    @Mock
    private io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure
        .UserSubscriptionRepository userSubscriptionRepository;

    @InjectMocks
    private SubscriptionWebhookTxService webhookTxService;

    private static final LocalDateTime NOW = LocalDateTime.now();

    private UserSubscription row(LocalDateTime expiresAt) {
        return UserSubscription.builder()
            .userId("user-1")
            .platform("ios")
            .productId("membership_1m")
            .plan(SubscriptionPlan.MONTHLY)
            .startedAt(NOW.minusMonths(2))
            .expiresAt(expiresAt)
            .autoRenew(true)
            .trialUsed(false)
            .originalTransactionId("orig-tx-001")
            .purchaseToken(null)
            .build();
    }

    private static long millis(LocalDateTime ldt) {
        return ldt.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private JWSTransactionDecodedPayload transaction(String productId, LocalDateTime expiresAt) {
        return new JWSTransactionDecodedPayload()
            .originalTransactionId("orig-tx-001")
            .productId(productId)
            .expiresDate(millis(expiresAt));
    }

    @Nested
    @DisplayName("Apple (ASSN V2)")
    class AppleTest {

        @Test
        @DisplayName("DID_RENEW — 만료 연장 + 유예 해제, 플랜 동기화")
        void didRenewExtends() {
            UserSubscription sub = row(NOW.minusDays(1));
            sub.enterGracePeriod(NOW.plusDays(10));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            LocalDateTime newExpiry = NOW.plusMonths(1).withNano(0);
            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "DID_RENEW", "BILLING_RECOVERY",
                transaction("membership_1y", newExpiry),
                new JWSRenewalInfoDecodedPayload().autoRenewStatus(AutoRenewStatus.ON)));

            assertThat(sub.getExpiresAt()).isEqualTo(newExpiry);
            assertThat(sub.getGracePeriodExpiresAt()).isNull();
            assertThat(sub.getPlan()).isEqualTo(SubscriptionPlan.ANNUAL);
            assertThat(sub.getProductId()).isEqualTo("membership_1y");
            assertThat(sub.getAutoRenew()).isTrue();
        }

        @Test
        @DisplayName("DID_CHANGE_RENEWAL_STATUS(해지) — 자동갱신만 끄고 만료까지 권한 유지")
        void renewalStatusDisabled() {
            UserSubscription sub = row(NOW.plusDays(20));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "DID_CHANGE_RENEWAL_STATUS", "AUTO_RENEW_DISABLED",
                transaction("membership_1m", NOW.plusDays(20)),
                new JWSRenewalInfoDecodedPayload().autoRenewStatus(AutoRenewStatus.OFF)));

            assertThat(sub.getAutoRenew()).isFalse();
            assertThat(sub.getExpiresAt()).isAfter(NOW); // 권한은 유지
        }

        @Test
        @DisplayName("DID_FAIL_TO_RENEW(GRACE_PERIOD) — 유예기간 진입")
        void failToRenewEntersGrace() {
            UserSubscription sub = row(NOW.minusHours(1));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            LocalDateTime graceUntil = NOW.plusDays(16).withNano(0);
            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "DID_FAIL_TO_RENEW", "GRACE_PERIOD",
                transaction("membership_1m", NOW.minusHours(1)),
                new JWSRenewalInfoDecodedPayload().gracePeriodExpiresDate(millis(graceUntil))));

            assertThat(sub.getGracePeriodExpiresAt()).isEqualTo(graceUntil);
            assertThat(sub.isEntitled(NOW)).isTrue();
        }

        @Test
        @DisplayName("GRACE_PERIOD_EXPIRED — 유예 해제 + 자동갱신 끔")
        void gracePeriodExpiredClears() {
            UserSubscription sub = row(NOW.minusDays(10));
            sub.enterGracePeriod(NOW.minusMinutes(1));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "GRACE_PERIOD_EXPIRED", null,
                transaction("membership_1m", NOW.minusDays(10)), null));

            assertThat(sub.getGracePeriodExpiresAt()).isNull();
            assertThat(sub.getAutoRenew()).isFalse();
            assertThat(sub.isEntitled(NOW)).isFalse();
        }

        @Test
        @DisplayName("REFUND — 권한 즉시 종료 (만료를 회수 시각으로 당김)")
        void refundRevokes() {
            UserSubscription sub = row(NOW.plusDays(20));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            LocalDateTime revokedAt = NOW.minusHours(2).withNano(0);
            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "REFUND", null,
                transaction("membership_1m", NOW.plusDays(20)).revocationDate(millis(revokedAt)),
                null));

            assertThat(sub.getExpiresAt()).isEqualTo(revokedAt);
            assertThat(sub.getAutoRenew()).isFalse();
            assertThat(sub.isEntitled(NOW)).isFalse();
        }

        @Test
        @DisplayName("PRICE_INCREASE(ACCEPTED, 가격 변경 동의) — 상태 변화 없음")
        void priceIncreaseNoop() {
            UserSubscription sub = row(NOW.plusDays(20));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "PRICE_INCREASE", "ACCEPTED",
                transaction("membership_1m", NOW.plusDays(20)), null));

            assertThat(sub.getExpiresAt()).isEqualTo(NOW.plusDays(20));
            assertThat(sub.getAutoRenew()).isTrue();
        }

        @Test
        @DisplayName("매칭 행이 없으면 예외 없이 스킵한다 (verify 이전 구매)")
        void missingRowIsNoop() {
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.empty());

            assertThatCode(() -> webhookTxService.applyAppleNotification(
                new AppleSubscriptionNotification(
                    "DID_RENEW", null, transaction("membership_1m", NOW.plusMonths(1)), null)))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Google (RTDN)")
    class GoogleTest {

        private UserSubscription androidRow(LocalDateTime expiresAt) {
            UserSubscription sub = row(expiresAt);
            sub.setPlatform("android");
            sub.setProductId("membership");
            sub.setBasePlanId("1m");
            sub.setOriginalTransactionId(null);
            sub.setPurchaseToken("token-001");
            return sub;
        }

        @Test
        @DisplayName("ACTIVE 상태 적용 — 만료/플랜/자동갱신 동기화 + 유예 해제")
        void activeStateApplied() {
            UserSubscription sub = androidRow(NOW.minusDays(1));
            sub.enterGracePeriod(NOW.plusDays(5));
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyGoogleState("token-001", new GoogleSubscriptionState(
                "membership", "1y", null, NOW.plusYears(1), true, false,
                "SUBSCRIPTION_STATE_ACTIVE"));

            assertThat(sub.getExpiresAt()).isEqualTo(NOW.plusYears(1));
            assertThat(sub.getPlan()).isEqualTo(SubscriptionPlan.ANNUAL);
            assertThat(sub.getBasePlanId()).isEqualTo("1y");
            assertThat(sub.getGracePeriodExpiresAt()).isNull();
        }

        @Test
        @DisplayName("IN_GRACE_PERIOD — 유예기간 진입 (expiryTime을 유예 종료로 사용)")
        void gracePeriodStateApplied() {
            UserSubscription sub = androidRow(NOW.minusDays(1));
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyGoogleState("token-001", new GoogleSubscriptionState(
                "membership", "1m", null, NOW.plusDays(14), true, false,
                "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"));

            assertThat(sub.getGracePeriodExpiresAt()).isEqualTo(NOW.plusDays(14));
        }

        @Test
        @DisplayName("CANCELED — 자동갱신 꺼짐 반영, 만료까지 권한 유지")
        void canceledStateApplied() {
            UserSubscription sub = androidRow(NOW.plusDays(20));
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyGoogleState("token-001", new GoogleSubscriptionState(
                "membership", "1m", null, NOW.plusDays(20), false, false,
                "SUBSCRIPTION_STATE_CANCELED"));

            assertThat(sub.getAutoRenew()).isFalse();
            assertThat(sub.isEntitled(NOW)).isTrue();
        }

        @Test
        @DisplayName("REVOKED/환불 — 권한 즉시 종료")
        void revokeEndsEntitlement() {
            UserSubscription sub = androidRow(NOW.plusDays(20));
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.revokeByPurchaseToken("token-001");

            assertThat(sub.getExpiresAt()).isBeforeOrEqualTo(LocalDateTime.now());
            assertThat(sub.getAutoRenew()).isFalse();
        }

        @Test
        @DisplayName("매칭 행이 없으면 예외 없이 스킵한다")
        void missingRowIsNoop() {
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.empty());

            assertThatCode(() -> webhookTxService.applyGoogleState("token-001",
                new GoogleSubscriptionState("membership", "1m", null, NOW.plusDays(1), true, false,
                    "SUBSCRIPTION_STATE_ACTIVE")))
                .doesNotThrowAnyException();
            assertThatCode(() -> webhookTxService.revokeByPurchaseToken("token-001"))
                .doesNotThrowAnyException();
        }
    }
}
