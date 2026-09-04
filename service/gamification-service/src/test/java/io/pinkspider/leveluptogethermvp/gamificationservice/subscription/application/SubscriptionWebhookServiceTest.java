package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.AppleSubscriptionNotification;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleSubscriptionState;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionWebhookService 테스트 (LUT-452)")
class SubscriptionWebhookServiceTest {

    @Mock
    private SubscriptionVerificationService verificationService;

    @Mock
    private SubscriptionWebhookTxService webhookTxService;

    @InjectMocks
    private SubscriptionWebhookService webhookService;

    private static String b64(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("Apple (ASSN V2)")
    class AppleTest {

        @Test
        @DisplayName("서명 검증 통과 알림은 적용 서비스로 넘긴다")
        void validNotificationApplied() {
            AppleSubscriptionNotification notification = new AppleSubscriptionNotification(
                "DID_RENEW", null,
                new JWSTransactionDecodedPayload().originalTransactionId("orig-tx-001"), null);
            when(verificationService.decodeAppleNotification("signed")).thenReturn(notification);

            webhookService.handleAppleNotification("signed");

            verify(webhookTxService).applyAppleNotification(notification);
        }

        @Test
        @DisplayName("서명 검증 실패는 삼키고 정상 반환한다 — 재전송 무의미")
        void signatureFailureAcked() {
            when(verificationService.decodeAppleNotification("bad"))
                .thenThrow(new CustomException("120702", "error.iap.verification_failed"));

            assertThatCode(() -> webhookService.handleAppleNotification("bad"))
                .doesNotThrowAnyException();
            verify(webhookTxService, never()).applyAppleNotification(any());
        }

        @Test
        @DisplayName("TEST 알림은 적용 없이 수신만 확인한다")
        void testNotificationAcked() {
            when(verificationService.decodeAppleNotification("signed"))
                .thenReturn(new AppleSubscriptionNotification("TEST", null, null, null));

            webhookService.handleAppleNotification("signed");

            verify(webhookTxService, never()).applyAppleNotification(any());
        }

        @Test
        @DisplayName("빈 payload는 스킵한다")
        void blankPayloadSkipped() {
            webhookService.handleAppleNotification(" ");

            verify(webhookTxService, never()).applyAppleNotification(any());
        }
    }

    @Nested
    @DisplayName("Google (RTDN)")
    class GoogleTest {

        @Test
        @DisplayName("구독 알림은 Play API 재조회 후 상태를 적용한다 (RTDN은 트리거)")
        void subscriptionNotificationRefetchesAndApplies() {
            GoogleSubscriptionState state = new GoogleSubscriptionState(
                "membership", "1m", null, LocalDateTime.now().plusMonths(1), true, false,
                "SUBSCRIPTION_STATE_ACTIVE");
            when(verificationService.fetchGoogleSubscription("token-001")).thenReturn(state);

            webhookService.handleGoogleNotification(b64(
                "{\"version\":\"1.0\",\"packageName\":\"pkg\","
                    + "\"subscriptionNotification\":{\"version\":\"1.0\",\"notificationType\":2,"
                    + "\"purchaseToken\":\"token-001\",\"subscriptionId\":\"membership\"}}"));

            verify(webhookTxService).applyGoogleState(eq("token-001"), eq(state));
        }

        @Test
        @DisplayName("REVOKED(12)는 재조회 없이 즉시 권한 종료한다")
        void revokedEndsImmediately() {
            webhookService.handleGoogleNotification(b64(
                "{\"subscriptionNotification\":{\"notificationType\":12,"
                    + "\"purchaseToken\":\"token-001\"}}"));

            verify(webhookTxService).revokeByPurchaseToken("token-001");
            verify(verificationService, never()).fetchGoogleSubscription(anyString());
        }

        @Test
        @DisplayName("voidedPurchaseNotification(환불)은 권한을 종료한다")
        void voidedPurchaseRevokes() {
            webhookService.handleGoogleNotification(b64(
                "{\"voidedPurchaseNotification\":{\"purchaseToken\":\"token-001\","
                    + "\"orderId\":\"GPA.1234\",\"productType\":1}}"));

            verify(webhookTxService).revokeByPurchaseToken("token-001");
        }

        @Test
        @DisplayName("가격 변경 동의(8)도 최신 상태로 동기화한다")
        void priceChangeConfirmedSyncs() {
            GoogleSubscriptionState state = new GoogleSubscriptionState(
                "membership", "1m", null, LocalDateTime.now().plusMonths(1), true, false,
                "SUBSCRIPTION_STATE_ACTIVE");
            when(verificationService.fetchGoogleSubscription("token-001")).thenReturn(state);

            webhookService.handleGoogleNotification(b64(
                "{\"subscriptionNotification\":{\"notificationType\":8,"
                    + "\"purchaseToken\":\"token-001\"}}"));

            verify(webhookTxService).applyGoogleState(eq("token-001"), eq(state));
        }

        @Test
        @DisplayName("테스트 알림은 수신만 확인한다")
        void testNotificationAcked() {
            webhookService.handleGoogleNotification(b64("{\"testNotification\":{\"version\":\"1.0\"}}"));

            verify(webhookTxService, never()).applyGoogleState(anyString(), any());
        }

        @Test
        @DisplayName("잘못된 base64/JSON은 삼키고 정상 반환한다 — 재전송 무의미")
        void malformedPayloadAcked() {
            assertThatCode(() -> webhookService.handleGoogleNotification("!!not-base64!!"))
                .doesNotThrowAnyException();
            assertThatCode(() -> webhookService.handleGoogleNotification(null))
                .doesNotThrowAnyException();
            verify(webhookTxService, never()).applyGoogleState(anyString(), any());
        }

        @Test
        @DisplayName("재조회 실패(잘못된 토큰)는 삼키고 정상 반환한다")
        void refetchFailureAcked() {
            when(verificationService.fetchGoogleSubscription("token-001"))
                .thenThrow(new CustomException("120702", "error.iap.verification_failed"));

            assertThatCode(() -> webhookService.handleGoogleNotification(b64(
                    "{\"subscriptionNotification\":{\"notificationType\":2,"
                        + "\"purchaseToken\":\"token-001\"}}")))
                .doesNotThrowAnyException();
            verify(webhookTxService, never()).applyGoogleState(anyString(), any());
        }
    }
}
