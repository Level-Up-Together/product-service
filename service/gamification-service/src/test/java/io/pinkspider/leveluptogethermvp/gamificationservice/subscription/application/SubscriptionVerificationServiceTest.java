package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.OfferType;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.IapAppleProperties;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerificationResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerifyRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@DisplayName("SubscriptionVerificationService 테스트 (LUT-451)")
class SubscriptionVerificationServiceTest {

    @TempDir
    Path tempDir;

    private SubscriptionVerificationService service(boolean enabled) {
        return new SubscriptionVerificationService(enabled, "io.pinkspider.lut", "", new IapAppleProperties());
    }

    private SubscriptionVerifyRequest iosRequest() {
        return SubscriptionVerifyRequest.builder()
            .platform("ios")
            .productId("membership_1m")
            .transactionId("tx-001")
            .build();
    }

    private SubscriptionVerifyRequest androidRequest() {
        return SubscriptionVerifyRequest.builder()
            .platform("android")
            .productId("membership")
            .purchaseToken("token-001")
            .basePlanId("1y")
            .build();
    }

    @Nested
    @DisplayName("검증 비활성 (dev 기본)")
    class DisabledTest {

        @Test
        @DisplayName("ios는 요청 값을 신뢰한다 — 만료는 null(플랜 기본 기간으로 대체됨)")
        void disabled_ios_trustsRequest() {
            SubscriptionVerificationResult result = service(false).verify(iosRequest());

            assertThat(result.storeProductId()).isEqualTo("membership_1m");
            assertThat(result.originalTransactionId()).isEqualTo("tx-001");
            assertThat(result.purchaseToken()).isNull();
            assertThat(result.expiresAt()).isNull();
            assertThat(result.autoRenew()).isTrue();
            assertThat(result.trial()).isFalse();
        }

        @Test
        @DisplayName("android는 base_plan_id 힌트를 함께 신뢰한다 (3키 매핑용)")
        void disabled_android_trustsBasePlanHint() {
            SubscriptionVerificationResult result = service(false).verify(androidRequest());

            assertThat(result.storeProductId()).isEqualTo("membership");
            assertThat(result.basePlanId()).isEqualTo("1y");
            assertThat(result.purchaseToken()).isEqualTo("token-001");
            assertThat(result.originalTransactionId()).isNull();
        }

        @Test
        @DisplayName("트랜잭션 식별자가 없으면 비활성이라도 예외")
        void disabled_missingIdentifier_throws() {
            SubscriptionVerifyRequest request = SubscriptionVerifyRequest.builder()
                .platform("android")
                .productId("membership")
                .build();

            assertThatThrownBy(() -> service(false).verify(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.receipt_required");
        }
    }

    @Nested
    @DisplayName("Apple 검증 (App Store Server API)")
    class AppleTest {

        private JWSTransactionDecodedPayload payload() {
            return new JWSTransactionDecodedPayload()
                .productId("membership_1m")
                .originalTransactionId("orig-tx-001")
                .originalPurchaseDate(1756944000000L) // 2025-09-04T00:00:00Z
                .expiresDate(1759536000000L); // 2025-10-04T00:00:00Z
        }

        @Test
        @DisplayName("트랜잭션 payload를 결과로 매핑한다 — 만료/원트랜잭션/시작 시각")
        void apple_valid_mapsPayload() {
            SubscriptionVerificationService svc = spy(service(true));
            doReturn(payload()).when(svc).fetchAppleTransaction("tx-001");

            SubscriptionVerificationResult result = svc.verify(iosRequest());

            assertThat(result.storeProductId()).isEqualTo("membership_1m");
            assertThat(result.originalTransactionId()).isEqualTo("orig-tx-001");
            assertThat(result.expiresAt())
                .isEqualTo(LocalDateTime.of(2025, 10, 4, 0, 0, 0));
            assertThat(result.startedAt())
                .isEqualTo(LocalDateTime.of(2025, 9, 4, 0, 0, 0));
            assertThat(result.autoRenew()).isTrue();
            assertThat(result.trial()).isFalse();
        }

        @Test
        @DisplayName("introductory offer 구매는 무료 체험 사용으로 식별한다")
        void apple_introductoryOffer_marksTrial() {
            SubscriptionVerificationService svc = spy(service(true));
            doReturn(payload().offerType(OfferType.INTRODUCTORY_OFFER))
                .when(svc).fetchAppleTransaction("tx-001");

            assertThat(svc.verify(iosRequest()).trial()).isTrue();
        }

        @Test
        @DisplayName("요청 상품과 트랜잭션 상품이 다르면 불일치 예외")
        void apple_productMismatch_throws() {
            SubscriptionVerificationService svc = spy(service(true));
            doReturn(payload().productId("membership_1y")).when(svc).fetchAppleTransaction("tx-001");

            assertThatThrownBy(() -> svc.verify(iosRequest()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.product_mismatch");
        }

        @Test
        @DisplayName("만료가 없는 트랜잭션(비구독 상품)은 불일치 예외")
        void apple_noExpiry_throws() {
            SubscriptionVerificationService svc = spy(service(true));
            doReturn(payload().expiresDate(null)).when(svc).fetchAppleTransaction("tx-001");

            assertThatThrownBy(() -> svc.verify(iosRequest()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.product_mismatch");
        }

        @Test
        @DisplayName("자격증명이 없으면(클라이언트 생성 실패) 검증 실패 예외")
        void apple_missingCredentials_throws() {
            assertThatThrownBy(() -> service(true).verify(iosRequest()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.verification_failed");
        }
    }

    @Nested
    @DisplayName("Google 검증 (subscriptionsv2)")
    class GoogleTest {

        private SubscriptionVerificationService serviceWithGoogle(RestTemplate rest) throws Exception {
            SubscriptionVerificationService svc = new SubscriptionVerificationService(
                true, "io.pinkspider.lut", writeFakeServiceAccountJson().toString(),
                new IapAppleProperties());
            svc.setRestTemplate(rest);
            when(rest.postForObject(eq("https://oauth2.googleapis.com/token"), any(), eq(String.class)))
                .thenReturn("{\"access_token\":\"fake-token\"}");
            return svc;
        }

        @Test
        @DisplayName("활성 구독을 결과로 매핑한다 — base plan/만료/자동갱신")
        void google_active_mapsResponse() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                    contains("/purchases/subscriptionsv2/tokens/token-001"),
                    eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                    + "\"subscriptionState\":\"SUBSCRIPTION_STATE_ACTIVE\","
                    + "\"startTime\":\"2026-09-04T00:00:00Z\","
                    + "\"lineItems\":[{"
                    + "  \"productId\":\"membership\","
                    + "  \"expiryTime\":\"2027-09-04T00:00:00Z\","
                    + "  \"autoRenewingPlan\":{\"autoRenewEnabled\":true},"
                    + "  \"offerDetails\":{\"basePlanId\":\"1y\"}"
                    + "}]}"));

            SubscriptionVerificationResult result = svc.verify(androidRequest());

            assertThat(result.storeProductId()).isEqualTo("membership");
            assertThat(result.basePlanId()).isEqualTo("1y");
            assertThat(result.purchaseToken()).isEqualTo("token-001");
            assertThat(result.expiresAt()).isEqualTo(LocalDateTime.of(2027, 9, 4, 0, 0, 0));
            assertThat(result.startedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 0, 0, 0));
            assertThat(result.autoRenew()).isTrue();
            assertThat(result.trial()).isFalse();
        }

        @Test
        @DisplayName("offerId가 있으면 무료 체험/오퍼 구매로 식별하고, 해지 상태면 자동갱신 false")
        void google_offerAndCanceled_mapped() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                    contains("/purchases/subscriptionsv2/tokens/token-001"),
                    eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                    + "\"subscriptionState\":\"SUBSCRIPTION_STATE_CANCELED\","
                    + "\"lineItems\":[{"
                    + "  \"productId\":\"membership\","
                    + "  \"expiryTime\":\"2026-10-04T00:00:00Z\","
                    + "  \"autoRenewingPlan\":{\"autoRenewEnabled\":false},"
                    + "  \"offerDetails\":{\"basePlanId\":\"1y\",\"offerId\":\"free-trial\"}"
                    + "}]}"));

            SubscriptionVerificationResult result = svc.verify(androidRequest());

            assertThat(result.autoRenew()).isFalse();
            assertThat(result.trial()).isTrue();
        }

        @Test
        @DisplayName("플랜 변경으로 line item이 복수면 만료가 가장 늦은 항목을 쓴다")
        void google_multipleLineItems_usesLatestExpiry() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                    contains("/purchases/subscriptionsv2/tokens/token-001"),
                    eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                    + "\"subscriptionState\":\"SUBSCRIPTION_STATE_ACTIVE\","
                    + "\"lineItems\":["
                    + "{\"productId\":\"membership\",\"expiryTime\":\"2026-10-04T00:00:00Z\","
                    + " \"offerDetails\":{\"basePlanId\":\"1m\"}},"
                    + "{\"productId\":\"membership\",\"expiryTime\":\"2027-09-04T00:00:00Z\","
                    + " \"autoRenewingPlan\":{\"autoRenewEnabled\":true},"
                    + " \"offerDetails\":{\"basePlanId\":\"1y\"}}"
                    + "]}"));

            SubscriptionVerificationResult result = svc.verify(androidRequest());

            assertThat(result.basePlanId()).isEqualTo("1y");
            assertThat(result.expiresAt()).isEqualTo(LocalDateTime.of(2027, 9, 4, 0, 0, 0));
        }

        @Test
        @DisplayName("결제 대기(PENDING) 상태는 검증 실패로 처리한다")
        void google_pending_throws() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                    contains("/purchases/subscriptionsv2/tokens/token-001"),
                    eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(
                    "{\"subscriptionState\":\"SUBSCRIPTION_STATE_PENDING\"}"));

            assertThatThrownBy(() -> svc.verify(androidRequest()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.verification_failed");
        }

        @Test
        @DisplayName("응답 상품이 요청과 다르면 불일치 예외")
        void google_productMismatch_throws() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                    contains("/purchases/subscriptionsv2/tokens/token-001"),
                    eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                    + "\"subscriptionState\":\"SUBSCRIPTION_STATE_ACTIVE\","
                    + "\"lineItems\":[{\"productId\":\"other_product\","
                    + "\"expiryTime\":\"2026-10-04T00:00:00Z\"}]}"));

            assertThatThrownBy(() -> svc.verify(androidRequest()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.product_mismatch");
        }

        @Test
        @DisplayName("서비스 계정 설정이 없으면 검증 실패로 처리된다")
        void google_missingServiceAccount_throws() {
            assertThatThrownBy(() -> service(true).verify(androidRequest()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.verification_failed");
        }

        /** RSA 키를 즉석 생성해 서비스계정 JSON 파일을 만든다 (IapVerificationServiceTest 패턴) */
        private Path writeFakeServiceAccountJson() throws Exception {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            String privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

            Path serviceAccountFile = tempDir.resolve("service-account-" + System.nanoTime() + ".json");
            String json = "{\"client_email\":\"svc@example.iam.gserviceaccount.com\",\"private_key\":\""
                + privateKeyPem.replace("\n", "\\n") + "\"}";
            Files.writeString(serviceAccountFile, json);
            return serviceAccountFile;
        }
    }
}
