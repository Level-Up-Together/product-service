package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apple.itunes.storekit.model.AutoRenewStatus;
import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.AppleSubscriptionNotification;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleSubscriptionState;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
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

    @Mock
    private SubscriptionPaymentHistoryRecorder paymentHistoryRecorder;

    @Mock
    private SubscriptionGrantTxService grantTxService;

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

            LocalDateTime newExpiry = NOW.plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "DID_RENEW", "BILLING_RECOVERY",
                transaction("membership_1y", newExpiry),
                new JWSRenewalInfoDecodedPayload().autoRenewStatus(AutoRenewStatus.ON)));

            assertThat(sub.getExpiresAt()).isEqualTo(newExpiry);
            assertThat(sub.getGracePeriodExpiresAt()).isNull();
            assertThat(sub.getPlan()).isEqualTo(SubscriptionPlan.ANNUAL);
            assertThat(sub.getProductId()).isEqualTo("membership_1y");
            assertThat(sub.getAutoRenew()).isTrue();
            // LUT-486: 만료 엄격 연장 = RENEWAL 결제 이력 기록
            verify(paymentHistoryRecorder).record(
                eq(sub), eq(SubscriptionPaymentEventType.RENEWAL), eq(false),
                any(), any(), any(), eq(newExpiry), any());
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
            // LUT-486: 환불 = REFUND 결제 이력 기록 (회수 시각 기준)
            verify(paymentHistoryRecorder).record(
                eq(sub), eq(SubscriptionPaymentEventType.REFUND), eq(false),
                any(), any(), any(), eq(revokedAt), eq(revokedAt));
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

        // LUT-507: 결제 알림의 appAccountToken 이 다른 앱 계정이면 결제한 계정으로 이전을 시도한다
        @Test
        @DisplayName("LUT-507: SUBSCRIBED 거래의 appAccountToken 이 다른 계정이면 그 계정으로 이전(upsert)한다")
        void subscribedWithOtherPayerTokenTransfers() {
            UserSubscription expired = row(NOW.minusDays(2));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(expired));
            java.util.UUID payer = java.util.UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
            JWSTransactionDecodedPayload tx =
                transaction("membership_1m", NOW.plusMonths(1)).appAccountToken(payer)
                    .transactionId("tx-900");
            when(grantTxService.upsert(eq(payer.toString()), eq(SubscriptionPlan.MONTHLY), eq("ios"),
                    any(), eq(NOW.plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS)), any()))
                .thenReturn(row(NOW.plusMonths(1)));

            webhookTxService.applyAppleNotification(
                new AppleSubscriptionNotification("SUBSCRIBED", "RESUBSCRIBE", tx, null));

            verify(grantTxService).upsert(eq(payer.toString()), eq(SubscriptionPlan.MONTHLY), eq("ios"),
                any(), eq(NOW.plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS)), any());
            // 옛 주인 행은 건드리지 않는다 (이전은 upsert 가 처리)
            assertThat(expired.getExpiresAt()).isEqualTo(NOW.minusDays(2));
        }

        @Test
        @DisplayName("LUT-507: 이전이 거절되면(옛 주인 권한 보유, 120802) 기존 행에 그대로 동기화한다")
        void transferRejectedFallsBackToOwnerRow() {
            UserSubscription active = row(NOW.plusDays(10));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(active));
            java.util.UUID payer = java.util.UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
            JWSTransactionDecodedPayload tx =
                transaction("membership_1m", NOW.plusMonths(1)).appAccountToken(payer);
            when(grantTxService.upsert(any(), any(), any(), any(), any(), any()))
                .thenThrow(new io.pinkspider.global.exception.CustomException(
                    "120802", "error.subscription.transaction_already_used"));

            assertThatCode(() -> webhookTxService.applyAppleNotification(
                    new AppleSubscriptionNotification("DID_RENEW", null, tx, null)))
                .doesNotThrowAnyException();

            assertThat(active.getExpiresAt()).isEqualTo(NOW.plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        }

        @Test
        @DisplayName("LUT-507: appAccountToken 이 현재 주인이면 이전 없이 동기화한다")
        void ownerTokenNoTransfer() {
            UserSubscription mine = row(NOW.plusDays(10));
            mine.setUserId("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(mine));
            JWSTransactionDecodedPayload tx =
                transaction("membership_1m", NOW.plusMonths(1))
                    .appAccountToken(java.util.UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));

            webhookTxService.applyAppleNotification(
                new AppleSubscriptionNotification("DID_RENEW", null, tx, null));

            verify(grantTxService, never()).upsert(any(), any(), any(), any(), any(), any());
            assertThat(mine.getExpiresAt()).isEqualTo(NOW.plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
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
            // LUT-486: 만료 엄격 연장 = RENEWAL 결제 이력 기록 (Google 은 가격 미제공 → null)
            verify(paymentHistoryRecorder).record(
                eq(sub), eq(SubscriptionPaymentEventType.RENEWAL), eq(false),
                eq(null), eq(null), eq(null), eq(NOW.plusYears(1)), any());
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
            // LUT-486: 만료 연장 없는 상태 변경(해지 등)은 결제 이력을 남기지 않는다
            verify(paymentHistoryRecorder, never()).record(
                any(), any(), anyBoolean(), any(), any(), any(), any(), any());
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
            // LUT-486: 환불 = REFUND 결제 이력 기록
            verify(paymentHistoryRecorder).record(
                eq(sub), eq(SubscriptionPaymentEventType.REFUND), eq(false),
                eq(null), eq(null), eq(null), any(), any());
        }

        // LUT-499: 재구독·플랜 변경은 새 purchaseToken 을 발급하고 옛 토큰을 linkedPurchaseToken 으로 가리킨다.
        // 새 토큰으로 온 알림을 옛 토큰 행에 이어 붙여야 이력이 갈라지지 않는다.
        // LUT-507: 만료된 옛 주인의 옛 토큰(linkedPurchaseToken)으로 매칭됐지만 새 결제의 obfuscatedExternalAccountId 가
        // 다른 앱 계정이면 옛 행에 이어 붙이지 않고 결제한 계정으로 이전한다 (옛 주인 권한이 되살아나면 안 된다)
        @Test
        @DisplayName("LUT-507: 새 결제의 앱 계정 토큰이 다른 계정이면 연속성 키로 잇지 않고 그 계정으로 이전한다")
        void googleResubscribeByOtherAccountTransfers() {
            UserSubscription expiredOld = androidRow(NOW.minusDays(5));
            when(userSubscriptionRepository.findByPurchaseToken("token-002"))
                .thenReturn(Optional.empty());
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(expiredOld));
            String payer = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
            when(grantTxService.upsert(eq(payer), eq(SubscriptionPlan.ANNUAL), eq("android"),
                    any(), eq(NOW.plusYears(1)), any()))
                .thenReturn(androidRow(NOW.plusYears(1)));

            webhookTxService.applyGoogleState("token-002", new GoogleSubscriptionState(
                "membership", "1y", null, NOW.plusYears(1), true, false,
                "SUBSCRIPTION_STATE_ACTIVE", "token-001", "GPA.2222", payer));

            verify(grantTxService).upsert(eq(payer), eq(SubscriptionPlan.ANNUAL), eq("android"),
                any(), eq(NOW.plusYears(1)), any());
            // 옛 주인 행은 토큰 교체·만료 연장 없이 그대로
            assertThat(expiredOld.getPurchaseToken()).isEqualTo("token-001");
            assertThat(expiredOld.getExpiresAt()).isEqualTo(NOW.minusDays(5));
        }

        @Test
        @DisplayName("LUT-499: 새 토큰이 매칭 안 되면 linkedPurchaseToken 행에 이어 붙이고 토큰을 교체한다")
        void linkedPurchaseTokenContinuity() {
            UserSubscription sub = androidRow(NOW.minusDays(1));
            when(userSubscriptionRepository.findByPurchaseToken("token-002"))
                .thenReturn(Optional.empty());
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyGoogleState("token-002", new GoogleSubscriptionState(
                "membership", "1y", null, NOW.plusYears(1), true, false,
                "SUBSCRIPTION_STATE_ACTIVE", "token-001", "GPA.1234-5678"));

            assertThat(sub.getPurchaseToken()).isEqualTo("token-002");
            assertThat(sub.getPlan()).isEqualTo(SubscriptionPlan.ANNUAL);
            assertThat(sub.getExpiresAt()).isEqualTo(NOW.plusYears(1));
            // 거래 ID = latestOrderId
            verify(paymentHistoryRecorder).record(
                eq(sub), eq(SubscriptionPaymentEventType.RENEWAL), eq(false),
                eq(null), eq(null), eq("GPA.1234-5678"), eq(NOW.plusYears(1)), any());
        }

        @Test
        @DisplayName("LUT-499: 갱신 이력의 거래 ID 로 latestOrderId 를 기록한다")
        void latestOrderIdRecorded() {
            UserSubscription sub = androidRow(NOW.minusDays(1));
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyGoogleState("token-001", new GoogleSubscriptionState(
                "membership", "1m", null, NOW.plusMonths(1), true, false,
                "SUBSCRIPTION_STATE_ACTIVE", null, "GPA.9999-0001"));

            verify(paymentHistoryRecorder).record(
                eq(sub), eq(SubscriptionPaymentEventType.RENEWAL), eq(false),
                eq(null), eq(null), eq("GPA.9999-0001"), eq(NOW.plusMonths(1)), any());
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

    // LUT-499: 자가 치유 — Get All Subscription Statuses 스냅샷을 웹훅과 같은 규칙으로 반영한다
    @Nested
    @DisplayName("Apple 스냅샷 반영 (LUT-499 자가 치유)")
    class AppleSnapshotTest {

        @Test
        @DisplayName("최신 트랜잭션으로 만료를 연장하고 갱신 정보의 autoRenew 를 반영한다")
        void snapshotExtendsExpiry() {
            // JWS 만료는 ms 단위라 비교 기준도 ms 로 절삭한다
            LocalDateTime target =
                NOW.plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            UserSubscription sub = row(NOW.minusDays(1));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));
            JWSRenewalInfoDecodedPayload renewal = new JWSRenewalInfoDecodedPayload()
                .autoRenewStatus(AutoRenewStatus.ON);

            webhookTxService.applyAppleSnapshot("orig-tx-001",
                new io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto
                    .AppleSubscriptionSnapshot(transaction("membership_1m", target), renewal));

            assertThat(sub.getExpiresAt()).isEqualTo(target);
            assertThat(sub.getAutoRenew()).isTrue();
            verify(paymentHistoryRecorder).record(
                eq(sub), eq(SubscriptionPaymentEventType.RENEWAL), eq(false),
                any(), any(), any(), eq(target), any());
        }

        @Test
        @DisplayName("환불(revocationDate)된 트랜잭션이면 권한을 즉시 종료하고 REFUND 를 기록한다")
        void snapshotRevoked() {
            UserSubscription sub = row(NOW.plusDays(10));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));
            JWSTransactionDecodedPayload revoked = transaction("membership_1m", NOW.plusDays(10))
                .transactionId("tx-777")
                .revocationDate(millis(NOW.minusHours(1)));

            webhookTxService.applyAppleSnapshot("orig-tx-001",
                new io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto
                    .AppleSubscriptionSnapshot(revoked, null));

            assertThat(sub.isEntitled(NOW)).isFalse();
            assertThat(sub.getAutoRenew()).isFalse();
            verify(paymentHistoryRecorder).record(
                eq(sub), eq(SubscriptionPaymentEventType.REFUND), eq(false),
                eq(null), eq(null), eq("tx-777"), any(), any());
        }

        @Test
        @DisplayName("행이나 스냅샷이 없으면 예외 없이 스킵한다")
        void snapshotMissingIsNoop() {
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.empty());

            assertThatCode(() -> webhookTxService.applyAppleSnapshot("orig-tx-001",
                new io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto
                    .AppleSubscriptionSnapshot(transaction("membership_1m", NOW), null)))
                .doesNotThrowAnyException();
            assertThatCode(() -> webhookTxService.applyAppleSnapshot("orig-tx-001", null))
                .doesNotThrowAnyException();
        }
    }
}
