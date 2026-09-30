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

        @Test
        @DisplayName("행은 있지만 스냅샷이 null 이면 예외 없이 스킵한다")
        void snapshotNullWithRowIsNoop() {
            UserSubscription sub = row(NOW.plusDays(10));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            assertThatCode(() -> webhookTxService.applyAppleSnapshot("orig-tx-001", null))
                .doesNotThrowAnyException();

            assertThat(sub.getExpiresAt()).isEqualTo(NOW.plusDays(10));
            verify(paymentHistoryRecorder, never()).record(
                any(), any(), anyBoolean(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("행은 있지만 스냅샷의 트랜잭션이 null 이면 예외 없이 스킵한다")
        void snapshotWithoutTransactionIsNoop() {
            UserSubscription sub = row(NOW.plusDays(10));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            assertThatCode(() -> webhookTxService.applyAppleSnapshot("orig-tx-001",
                new io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto
                    .AppleSubscriptionSnapshot(null, null)))
                .doesNotThrowAnyException();

            assertThat(sub.getExpiresAt()).isEqualTo(NOW.plusDays(10));
        }
    }

    @Nested
    @DisplayName("Apple 분기 보강 — 알림 타입·payload 필드별")
    class AppleBranchTest {

        private JWSTransactionDecodedPayload bareTransaction() {
            return new JWSTransactionDecodedPayload().originalTransactionId("orig-tx-001");
        }

        @Test
        @DisplayName("트랜잭션이 없는 알림(TEST 등)은 조회 없이 스킵한다")
        void nullTransactionSkipped() {
            assertThatCode(() -> webhookTxService.applyAppleNotification(
                    new AppleSubscriptionNotification("TEST", null, null, null)))
                .doesNotThrowAnyException();

            verify(userSubscriptionRepository, never()).findByOriginalTransactionId(any());
        }

        @Test
        @DisplayName("originalTransactionId 가 없는 트랜잭션은 조회 없이 스킵한다")
        void nullOriginalTransactionIdSkipped() {
            JWSTransactionDecodedPayload tx =
                new JWSTransactionDecodedPayload().productId("membership_1m");

            assertThatCode(() -> webhookTxService.applyAppleNotification(
                    new AppleSubscriptionNotification("DID_RENEW", null, tx, null)))
                .doesNotThrowAnyException();

            verify(userSubscriptionRepository, never()).findByOriginalTransactionId(any());
        }

        @Test
        @DisplayName("EXPIRED — 만료 시각이 있으면 반영하고 유예·자동갱신을 끈다")
        void expiredWithExpiresDate() {
            UserSubscription sub = row(NOW.plusDays(20));
            sub.enterGracePeriod(NOW.plusDays(30));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));
            LocalDateTime expiredAt = NOW.minusHours(1).withNano(0);

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "EXPIRED", "VOLUNTARY", transaction("membership_1m", expiredAt), null));

            assertThat(sub.getExpiresAt()).isEqualTo(expiredAt);
            assertThat(sub.getGracePeriodExpiresAt()).isNull();
            assertThat(sub.getAutoRenew()).isFalse();
        }

        @Test
        @DisplayName("EXPIRED — 만료 시각이 없으면 기존 만료를 유지한 채 자동갱신만 끈다")
        void expiredWithoutExpiresDate() {
            UserSubscription sub = row(NOW.plusDays(20));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "EXPIRED", "VOLUNTARY", bareTransaction(), null));

            assertThat(sub.getExpiresAt()).isEqualTo(NOW.plusDays(20));
            assertThat(sub.getAutoRenew()).isFalse();
        }

        @Test
        @DisplayName("REVOKE — 회수 시각이 없으면 현재 시각으로 종료하고, 이미 만료된 행은 만료를 당기지 않는다")
        void revokeWithoutRevocationDateOnExpiredRow() {
            UserSubscription sub = row(NOW.minusDays(3));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "REVOKE", null, bareTransaction().transactionId("tx-rv"), null));

            // 회수 시각(now)이 기존 만료보다 뒤라 만료는 그대로
            assertThat(sub.getExpiresAt()).isEqualTo(NOW.minusDays(3));
            assertThat(sub.getAutoRenew()).isFalse();
            verify(paymentHistoryRecorder).record(
                eq(sub), eq(SubscriptionPaymentEventType.REFUND), eq(false),
                eq(null), eq(null), eq("tx-rv"), any(), any());
        }

        @Test
        @DisplayName("OFFER_REDEEMED — 소개 오퍼(rawOfferType=1)면 체험 사용 표시 + 가격·구매시각으로 이력 기록")
        void offerRedeemedIntroductoryTrial() {
            UserSubscription sub = row(NOW.minusDays(1));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));
            LocalDateTime newExpiry =
                NOW.plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            LocalDateTime purchasedAt =
                NOW.minusMinutes(5).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            JWSTransactionDecodedPayload tx = transaction("membership_1m", newExpiry)
                .transactionId("tx-offer")
                .price(4900000L)
                .currency("KRW")
                .purchaseDate(millis(purchasedAt));
            tx.setRawOfferType(1);

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "OFFER_REDEEMED", "INITIAL_BUY", tx, null));

            assertThat(sub.getTrialUsed()).isTrue();
            assertThat(sub.getExpiresAt()).isEqualTo(newExpiry);
            verify(paymentHistoryRecorder).record(
                eq(sub), eq(SubscriptionPaymentEventType.RENEWAL), eq(true),
                eq(new java.math.BigDecimal("4900.000")), eq("KRW"), eq("tx-offer"),
                eq(newExpiry), eq(purchasedAt));
        }

        @Test
        @DisplayName("DID_RENEW — 프로모션 오퍼(rawOfferType=2)는 체험으로 치지 않는다")
        void promotionalOfferIsNotTrial() {
            UserSubscription sub = row(NOW.minusDays(1));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));
            JWSTransactionDecodedPayload tx = transaction("membership_1m", NOW.plusMonths(1));
            tx.setRawOfferType(2);

            webhookTxService.applyAppleNotification(
                new AppleSubscriptionNotification("DID_RENEW", null, tx, null));

            assertThat(sub.getTrialUsed()).isFalse();
            verify(paymentHistoryRecorder).record(
                eq(sub), eq(SubscriptionPaymentEventType.RENEWAL), eq(false),
                any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("DID_CHANGE_RENEWAL_PREF — 상품·만료가 없는 트랜잭션은 플랜/만료를 건드리지 않고 이력도 없다")
        void renewalPrefWithoutProductAndExpiry() {
            UserSubscription sub = row(NOW.plusDays(10));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "DID_CHANGE_RENEWAL_PREF", "DOWNGRADE", bareTransaction(), null));

            assertThat(sub.getPlan()).isEqualTo(SubscriptionPlan.MONTHLY);
            assertThat(sub.getProductId()).isEqualTo("membership_1m");
            assertThat(sub.getExpiresAt()).isEqualTo(NOW.plusDays(10));
            verify(paymentHistoryRecorder, never()).record(
                any(), any(), anyBoolean(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("SUBSCRIBED — 앱 계정 토큰이 없으면 이전 없이 트랜잭션 기준으로 동기화한다")
        void subscribedWithoutTokenSyncs() {
            UserSubscription sub = row(NOW.minusDays(1));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));
            LocalDateTime newExpiry =
                NOW.plusYears(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "SUBSCRIBED", "INITIAL_BUY", transaction("membership_1y", newExpiry), null));

            verify(grantTxService, never()).upsert(any(), any(), any(), any(), any(), any());
            assertThat(sub.getPlan()).isEqualTo(SubscriptionPlan.ANNUAL);
            assertThat(sub.getExpiresAt()).isEqualTo(newExpiry);
        }

        @Test
        @DisplayName("DID_FAIL_TO_RENEW — renewalInfo 가 없으면 유예기간에 진입하지 않는다")
        void failToRenewWithoutRenewalInfo() {
            UserSubscription sub = row(NOW.minusHours(1));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "DID_FAIL_TO_RENEW", null, transaction("membership_1m", NOW.minusHours(1)), null));

            assertThat(sub.getGracePeriodExpiresAt()).isNull();
        }

        @Test
        @DisplayName("DID_FAIL_TO_RENEW — renewalInfo 에 유예 종료 시각이 없으면 유예기간에 진입하지 않는다")
        void failToRenewWithoutGraceDate() {
            UserSubscription sub = row(NOW.minusHours(1));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "DID_FAIL_TO_RENEW", null, transaction("membership_1m", NOW.minusHours(1)),
                new JWSRenewalInfoDecodedPayload()));

            assertThat(sub.getGracePeriodExpiresAt()).isNull();
        }

        @Test
        @DisplayName("DID_CHANGE_RENEWAL_STATUS — renewalInfo 에 autoRenewStatus 가 없으면 기존 값을 유지한다")
        void renewalStatusWithoutValueKept() {
            UserSubscription sub = row(NOW.plusDays(20));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "DID_CHANGE_RENEWAL_STATUS", null, transaction("membership_1m", NOW.plusDays(20)),
                new JWSRenewalInfoDecodedPayload()));

            assertThat(sub.getAutoRenew()).isTrue();
        }

        @Test
        @DisplayName("미처리 타입(CONSUMPTION_REQUEST 등)은 상태 변화 없이 스킵한다")
        void unknownTypeSkipped() {
            UserSubscription sub = row(NOW.plusDays(20));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "CONSUMPTION_REQUEST", null, transaction("membership_1m", NOW.plusDays(20)), null));

            assertThat(sub.getExpiresAt()).isEqualTo(NOW.plusDays(20));
            assertThat(sub.getAutoRenew()).isTrue();
            verify(paymentHistoryRecorder, never()).record(
                any(), any(), anyBoolean(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("LUT-507 Apple 소유권 이전 판정 분기")
    class AppleTransferBranchTest {

        private static final String PAYER = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

        @Test
        @DisplayName("다른 계정 토큰이라도 만료 시각이 없으면 이전하지 않고 기존 행에 동기화한다")
        void otherTokenWithoutExpiryNoTransfer() {
            UserSubscription sub = row(NOW.plusDays(10));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));
            JWSTransactionDecodedPayload tx = new JWSTransactionDecodedPayload()
                .originalTransactionId("orig-tx-001")
                .productId("membership_1y")
                .appAccountToken(java.util.UUID.fromString(PAYER));

            webhookTxService.applyAppleNotification(
                new AppleSubscriptionNotification("SUBSCRIBED", null, tx, null));

            verify(grantTxService, never()).upsert(any(), any(), any(), any(), any(), any());
            assertThat(sub.getPlan()).isEqualTo(SubscriptionPlan.ANNUAL);
            assertThat(sub.getExpiresAt()).isEqualTo(NOW.plusDays(10));
        }

        @Test
        @DisplayName("다른 계정 토큰이라도 상품 ID 가 없으면 이전하지 않고 만료만 연장한다")
        void otherTokenWithoutProductNoTransfer() {
            UserSubscription sub = row(NOW.plusDays(10));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(sub));
            LocalDateTime newExpiry =
                NOW.plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            JWSTransactionDecodedPayload tx = new JWSTransactionDecodedPayload()
                .originalTransactionId("orig-tx-001")
                .expiresDate(millis(newExpiry))
                .appAccountToken(java.util.UUID.fromString(PAYER));

            webhookTxService.applyAppleNotification(
                new AppleSubscriptionNotification("DID_RENEW", null, tx, null));

            verify(grantTxService, never()).upsert(any(), any(), any(), any(), any(), any());
            assertThat(sub.getPlan()).isEqualTo(SubscriptionPlan.MONTHLY);
            assertThat(sub.getExpiresAt()).isEqualTo(newExpiry);
        }

        @Test
        @DisplayName("이전 시 소개 오퍼·원구매일·갱신정보 없음 → trial=true, autoRenew=true 로 upsert 한다")
        void transferCarriesTrialAndOriginalPurchaseDate() {
            UserSubscription expired = row(NOW.minusDays(2));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(expired));
            LocalDateTime newExpiry =
                NOW.plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            LocalDateTime originalPurchase =
                NOW.minusDays(40).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            JWSTransactionDecodedPayload tx = transaction("membership_1m", newExpiry)
                .appAccountToken(java.util.UUID.fromString(PAYER))
                .originalPurchaseDate(millis(originalPurchase));
            tx.setRawOfferType(1);
            when(grantTxService.upsert(any(), any(), any(), any(), any(), any()))
                .thenReturn(row(newExpiry));

            webhookTxService.applyAppleNotification(
                new AppleSubscriptionNotification("OFFER_REDEEMED", null, tx, null));

            org.mockito.ArgumentCaptor<io.pinkspider.leveluptogethermvp.gamificationservice
                .subscription.domain.dto.SubscriptionVerificationResult> captor =
                org.mockito.ArgumentCaptor.forClass(
                    io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto
                        .SubscriptionVerificationResult.class);
            verify(grantTxService).upsert(eq(PAYER), eq(SubscriptionPlan.MONTHLY), eq("ios"),
                captor.capture(), eq(newExpiry), any());
            assertThat(captor.getValue().trial()).isTrue();
            assertThat(captor.getValue().autoRenew()).isTrue();
            assertThat(captor.getValue().startedAt()).isEqualTo(originalPurchase);
            assertThat(captor.getValue().appAccountToken()).isEqualTo(PAYER);
        }

        @Test
        @DisplayName("이전 시 갱신정보의 autoRenewStatus 가 없으면 autoRenew=true 로 본다")
        void transferRenewalInfoWithoutStatusIsAutoRenew() {
            UserSubscription expired = row(NOW.minusDays(2));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(expired));
            LocalDateTime newExpiry =
                NOW.plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            JWSTransactionDecodedPayload tx = transaction("membership_1m", newExpiry)
                .appAccountToken(java.util.UUID.fromString(PAYER));
            tx.setRawOfferType(2);
            when(grantTxService.upsert(any(), any(), any(), any(), any(), any()))
                .thenReturn(row(newExpiry));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "DID_RENEW", null, tx, new JWSRenewalInfoDecodedPayload()));

            org.mockito.ArgumentCaptor<io.pinkspider.leveluptogethermvp.gamificationservice
                .subscription.domain.dto.SubscriptionVerificationResult> captor =
                org.mockito.ArgumentCaptor.forClass(
                    io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto
                        .SubscriptionVerificationResult.class);
            verify(grantTxService).upsert(any(), any(), any(), captor.capture(), any(), any());
            assertThat(captor.getValue().autoRenew()).isTrue();
            assertThat(captor.getValue().trial()).isFalse();
        }

        @Test
        @DisplayName("이전 시 갱신정보 autoRenewStatus=OFF 면 autoRenew=false 로 upsert 한다")
        void transferRenewalInfoOff() {
            UserSubscription expired = row(NOW.minusDays(2));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(expired));
            LocalDateTime newExpiry =
                NOW.plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            JWSTransactionDecodedPayload tx = transaction("membership_1m", newExpiry)
                .appAccountToken(java.util.UUID.fromString(PAYER));
            when(grantTxService.upsert(any(), any(), any(), any(), any(), any()))
                .thenReturn(row(newExpiry));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "SUBSCRIBED", null, tx,
                new JWSRenewalInfoDecodedPayload().autoRenewStatus(AutoRenewStatus.OFF)));

            org.mockito.ArgumentCaptor<io.pinkspider.leveluptogethermvp.gamificationservice
                .subscription.domain.dto.SubscriptionVerificationResult> captor =
                org.mockito.ArgumentCaptor.forClass(
                    io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto
                        .SubscriptionVerificationResult.class);
            verify(grantTxService).upsert(any(), any(), any(), captor.capture(), any(), any());
            assertThat(captor.getValue().autoRenew()).isFalse();
        }

        @Test
        @DisplayName("이전 시 갱신정보 autoRenewStatus=ON 이면 autoRenew=true 로 upsert 한다")
        void transferRenewalInfoOn() {
            UserSubscription expired = row(NOW.minusDays(2));
            when(userSubscriptionRepository.findByOriginalTransactionId("orig-tx-001"))
                .thenReturn(Optional.of(expired));
            LocalDateTime newExpiry =
                NOW.plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            JWSTransactionDecodedPayload tx = transaction("membership_1m", newExpiry)
                .appAccountToken(java.util.UUID.fromString(PAYER));
            when(grantTxService.upsert(any(), any(), any(), any(), any(), any()))
                .thenReturn(row(newExpiry));

            webhookTxService.applyAppleNotification(new AppleSubscriptionNotification(
                "SUBSCRIBED", null, tx,
                new JWSRenewalInfoDecodedPayload().autoRenewStatus(AutoRenewStatus.ON)));

            org.mockito.ArgumentCaptor<io.pinkspider.leveluptogethermvp.gamificationservice
                .subscription.domain.dto.SubscriptionVerificationResult> captor =
                org.mockito.ArgumentCaptor.forClass(
                    io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto
                        .SubscriptionVerificationResult.class);
            verify(grantTxService).upsert(any(), any(), any(), captor.capture(), any(), any());
            assertThat(captor.getValue().autoRenew()).isTrue();
        }
    }

    @Nested
    @DisplayName("Google (RTDN) 분기 보강")
    class GoogleBranchTest {

        private static final String PAYER = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

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
        @DisplayName("LUT-499: linkedPurchaseToken 행도 없으면 스킵한다")
        void linkedTokenRowMissingSkipped() {
            when(userSubscriptionRepository.findByPurchaseToken("token-002"))
                .thenReturn(Optional.empty());
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.empty());

            assertThatCode(() -> webhookTxService.applyGoogleState("token-002",
                new GoogleSubscriptionState("membership", "1m", null, NOW.plusMonths(1), true,
                    false, "SUBSCRIPTION_STATE_ACTIVE", "token-001", "GPA.1")))
                .doesNotThrowAnyException();

            verify(paymentHistoryRecorder, never()).record(
                any(), any(), anyBoolean(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("PENDING 상태는 아무것도 반영하지 않고 스킵한다")
        void pendingStateSkipped() {
            UserSubscription sub = androidRow(NOW.plusDays(5));
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyGoogleState("token-001", new GoogleSubscriptionState(
                "membership", "1y", null, NOW.plusYears(1), true, false,
                GoogleSubscriptionState.STATE_PENDING));

            assertThat(sub.getExpiresAt()).isEqualTo(NOW.plusDays(5));
            assertThat(sub.getPlan()).isEqualTo(SubscriptionPlan.MONTHLY);
        }

        @Test
        @DisplayName("LUT-507: 앱 계정 토큰이 현재 주인이면 이전 없이 동기화한다")
        void ownerTokenNoTransfer() {
            UserSubscription sub = androidRow(NOW.minusDays(1));
            sub.setUserId(PAYER);
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyGoogleState("token-001", new GoogleSubscriptionState(
                "membership", "1m", null, NOW.plusMonths(1), true, false,
                "SUBSCRIPTION_STATE_ACTIVE", null, "GPA.5", PAYER));

            verify(grantTxService, never()).upsert(any(), any(), any(), any(), any(), any());
            assertThat(sub.getExpiresAt()).isEqualTo(NOW.plusMonths(1));
        }

        @Test
        @DisplayName("LUT-507: 다른 계정 토큰이라도 상품 ID 가 없으면 이전을 시도하지 않는다 (unknown_product 120801)")
        void otherTokenWithoutProductNoTransfer() {
            UserSubscription sub = androidRow(NOW.minusDays(1));
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(sub));

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    webhookTxService.applyGoogleState("token-001", new GoogleSubscriptionState(
                        null, "1m", null, NOW.plusMonths(1), true, false,
                        "SUBSCRIPTION_STATE_ACTIVE", null, "GPA.5", PAYER)))
                .isInstanceOf(io.pinkspider.global.exception.CustomException.class)
                .hasFieldOrPropertyWithValue("code", "120801");

            verify(grantTxService, never()).upsert(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("LUT-507: 이전이 거절되면(120802) 연속성 키로 옛 행에 이어 붙이고 동기화한다")
        void transferRejectedFallsBackToContinuity() {
            UserSubscription old = androidRow(NOW.plusDays(3));
            when(userSubscriptionRepository.findByPurchaseToken("token-002"))
                .thenReturn(Optional.empty());
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(old));
            when(grantTxService.upsert(any(), any(), any(), any(), any(), any()))
                .thenThrow(new io.pinkspider.global.exception.CustomException(
                    "120802", "error.subscription.transaction_already_used"));

            webhookTxService.applyGoogleState("token-002", new GoogleSubscriptionState(
                "membership", "1y", null, NOW.plusYears(1), true, false,
                "SUBSCRIPTION_STATE_ACTIVE", "token-001", "GPA.7", PAYER));

            assertThat(old.getPurchaseToken()).isEqualTo("token-002");
            assertThat(old.getPlan()).isEqualTo(SubscriptionPlan.ANNUAL);
            assertThat(old.getExpiresAt()).isEqualTo(NOW.plusYears(1));
        }

        @Test
        @DisplayName("체험(trial) 구매 상태면 trialUsed 를 켜고 이력에도 trial=true 로 남긴다")
        void trialMarksTrialUsed() {
            UserSubscription sub = androidRow(NOW.minusDays(1));
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.applyGoogleState("token-001", new GoogleSubscriptionState(
                "membership", "1m", null, NOW.plusDays(7), true, true,
                "SUBSCRIPTION_STATE_ACTIVE", null, "GPA.8"));

            assertThat(sub.getTrialUsed()).isTrue();
            verify(paymentHistoryRecorder).record(
                eq(sub), eq(SubscriptionPaymentEventType.RENEWAL), eq(true),
                eq(null), eq(null), eq("GPA.8"), eq(NOW.plusDays(7)), any());
        }

        @Test
        @DisplayName("이미 만료된 행의 환불은 만료를 당기지 않고 유예·자동갱신만 해제한다")
        void revokeOnExpiredRowKeepsExpiry() {
            UserSubscription sub = androidRow(NOW.minusDays(4));
            sub.enterGracePeriod(NOW.plusDays(1));
            when(userSubscriptionRepository.findByPurchaseToken("token-001"))
                .thenReturn(Optional.of(sub));

            webhookTxService.revokeByPurchaseToken("token-001");

            assertThat(sub.getExpiresAt()).isEqualTo(NOW.minusDays(4));
            assertThat(sub.getGracePeriodExpiresAt()).isNull();
            assertThat(sub.getAutoRenew()).isFalse();
        }
    }
}
