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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application.IapAppleProperties;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleSubscriptionState;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerificationResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerifyRequest;
import java.nio.charset.StandardCharsets;
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
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@DisplayName("SubscriptionVerificationService 테스트 (LUT-451)")
class SubscriptionVerificationServiceTest {

    @TempDir Path tempDir;

    private SubscriptionVerificationService service(boolean enabled) {
        return new SubscriptionVerificationService(
                enabled, "io.pinkspider.lut", "", new IapAppleProperties());
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
            SubscriptionVerifyRequest request =
                    SubscriptionVerifyRequest.builder()
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
            assertThat(result.expiresAt()).isEqualTo(LocalDateTime.of(2025, 10, 4, 0, 0, 0));
            assertThat(result.startedAt()).isEqualTo(LocalDateTime.of(2025, 9, 4, 0, 0, 0));
            assertThat(result.autoRenew()).isTrue();
            assertThat(result.trial()).isFalse();
        }

        @Test
        @DisplayName("LUT-507: 결제 시 실린 appAccountToken 을 결과로 넘긴다 (없으면 null)")
        void apple_appAccountToken_passedThrough() {
            SubscriptionVerificationService svc = spy(service(true));
            doReturn(
                            payload()
                                    .appAccountToken(
                                            java.util.UUID.fromString(
                                                    "4f43937f-3c7d-492a-ad0f-49e7b63a9c5c")))
                    .when(svc)
                    .fetchAppleTransaction("tx-001");

            SubscriptionVerificationResult result = svc.verify(iosRequest());

            assertThat(result.appAccountToken()).isEqualTo("4f43937f-3c7d-492a-ad0f-49e7b63a9c5c");
        }

        @Test
        @DisplayName("introductory offer 구매는 무료 체험 사용으로 식별한다")
        void apple_introductoryOffer_marksTrial() {
            SubscriptionVerificationService svc = spy(service(true));
            doReturn(payload().offerType(OfferType.INTRODUCTORY_OFFER))
                    .when(svc)
                    .fetchAppleTransaction("tx-001");

            assertThat(svc.verify(iosRequest()).trial()).isTrue();
        }

        @Test
        @DisplayName("요청 상품과 트랜잭션 상품이 다르면 불일치 예외")
        void apple_productMismatch_throws() {
            SubscriptionVerificationService svc = spy(service(true));
            doReturn(payload().productId("membership_1y"))
                    .when(svc)
                    .fetchAppleTransaction("tx-001");

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

        private SubscriptionVerificationService serviceWithGoogle(RestTemplate rest)
                throws Exception {
            SubscriptionVerificationService svc =
                    new SubscriptionVerificationService(
                            true,
                            "io.pinkspider.lut",
                            writeFakeServiceAccountJson().toString(),
                            new IapAppleProperties());
            svc.setRestTemplate(rest);
            when(rest.postForObject(
                            eq("https://oauth2.googleapis.com/token"), any(), eq(String.class)))
                    .thenReturn("{\"access_token\":\"fake-token\"}");
            return svc;
        }

        private static final String ORDER_JSON =
                "{\"orderId\":\"GPA.3329-9857-6705-37595..2\",\"state\":\"PROCESSED\","
                        + "\"createTime\":\"2026-10-26T03:27:03.120Z\","
                        + "\"total\":{\"currencyCode\":\"KRW\",\"units\":\"4900\"},"
                        + "\"lineItems\":[{\"productId\":\"membership\","
                        + "\"subscriptionDetails\":{\"basePlanId\":\"1m\","
                        + "\"servicePeriodStartTime\":\"2026-10-26T03:27:00Z\","
                        + "\"servicePeriodEndTime\":\"2026-10-26T03:32:00Z\"}}]}";

        @Test
        @DisplayName("LUT-617: orders.get 응답을 결제 시각·금액·이용 기간으로 매핑한다")
        void google_fetchOrder_mapsResponse() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                            contains("/orders/GPA.3329-9857-6705-37595..2"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenReturn(ResponseEntity.ok(ORDER_JSON));

            java.util.Optional<
                            io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain
                                    .dto.GoogleOrder>
                    order = svc.fetchGoogleOrder("GPA.3329-9857-6705-37595..2");

            assertThat(order).isPresent();
            assertThat(order.get().orderId()).isEqualTo("GPA.3329-9857-6705-37595..2");
            assertThat(order.get().state()).isEqualTo("PROCESSED");
            assertThat(order.get().createTime())
                    .isEqualTo(java.time.LocalDateTime.of(2026, 10, 26, 3, 27, 3, 120_000_000));
            assertThat(order.get().totalAmount()).isEqualByComparingTo("4900.00");
            assertThat(order.get().totalCurrency()).isEqualTo("KRW");
            assertThat(order.get().servicePeriodStart())
                    .isEqualTo(java.time.LocalDateTime.of(2026, 10, 26, 3, 27));
            assertThat(order.get().servicePeriodEnd())
                    .isEqualTo(java.time.LocalDateTime.of(2026, 10, 26, 3, 32));
        }

        @Test
        @DisplayName("LUT-617: 주문 404 는 empty, 그 외 4xx 는 120702, 5xx·네트워크는 StoreTransientException")
        void google_fetchOrder_errorClassification() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                            contains("/orders/missing"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenThrow(
                            org.springframework.web.client.HttpClientErrorException.create(
                                    org.springframework.http.HttpStatus.NOT_FOUND,
                                    "Not Found",
                                    org.springframework.http.HttpHeaders.EMPTY,
                                    null,
                                    null));
            when(rest.exchange(
                            contains("/orders/forbidden"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenThrow(
                            org.springframework.web.client.HttpClientErrorException.create(
                                    org.springframework.http.HttpStatus.FORBIDDEN,
                                    "Forbidden",
                                    org.springframework.http.HttpHeaders.EMPTY,
                                    null,
                                    null));
            when(rest.exchange(
                            contains("/orders/down"), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenThrow(
                            new org.springframework.web.client.HttpServerErrorException(
                                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
            when(rest.exchange(
                            contains("/orders/timeout"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenThrow(
                            new org.springframework.web.client.ResourceAccessException("timeout"));
            when(rest.exchange(
                            contains("/orders/garbage"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenReturn(ResponseEntity.ok("not-json"));

            assertThat(svc.fetchGoogleOrder("missing")).isEmpty();
            assertThatThrownBy(() -> svc.fetchGoogleOrder("forbidden"))
                    .isInstanceOf(CustomException.class)
                    .extracting("code")
                    .isEqualTo("120702");
            assertThatThrownBy(() -> svc.fetchGoogleOrder("down"))
                    .isInstanceOf(StoreTransientException.class);
            assertThatThrownBy(() -> svc.fetchGoogleOrder("timeout"))
                    .isInstanceOf(StoreTransientException.class);
            assertThatThrownBy(() -> svc.fetchGoogleOrder("garbage"))
                    .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName(
                "LUT-617: subscriptionsv2 5xx 는 StoreTransientException, verify 경로에선 120702 로 감싼다")
        void google_subscriptionTransient_isClassified() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                            contains("/purchases/subscriptionsv2/tokens/token-001"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenThrow(
                            new org.springframework.web.client.HttpServerErrorException(
                                    org.springframework.http.HttpStatus.BAD_GATEWAY));

            assertThatThrownBy(() -> svc.fetchGoogleSubscription("token-001"))
                    .isInstanceOf(StoreTransientException.class);
            assertThatThrownBy(() -> svc.verify(androidRequest()))
                    .isInstanceOf(CustomException.class)
                    .extracting("code")
                    .isEqualTo("120702");
        }

        @Test
        @DisplayName("LUT-617: subscriptionsv2 4xx(잘못된 토큰)는 영구 실패 120702")
        void google_subscriptionClientError_isPermanent() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                            contains("/purchases/subscriptionsv2/tokens/token-001"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenThrow(
                            org.springframework.web.client.HttpClientErrorException.create(
                                    org.springframework.http.HttpStatus.BAD_REQUEST,
                                    "Bad Request",
                                    org.springframework.http.HttpHeaders.EMPTY,
                                    null,
                                    null));

            assertThatThrownBy(() -> svc.fetchGoogleSubscription("token-001"))
                    .isInstanceOf(CustomException.class)
                    .extracting("code")
                    .isEqualTo("120702");
        }

        @Test
        @DisplayName("LUT-617: 액세스 토큰은 캐시돼 연속 호출에 한 번만 발급한다 — 발급 5xx 는 transient, 빈 토큰은 120702")
        void google_accessTokenCachedAndClassified() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(contains("/orders/"), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(ORDER_JSON));

            svc.fetchGoogleOrder("GPA.1..1");
            svc.fetchGoogleOrder("GPA.1..2");
            svc.fetchGoogleOrder("GPA.1..3");

            org.mockito.Mockito.verify(rest, org.mockito.Mockito.times(1))
                    .postForObject(
                            eq("https://oauth2.googleapis.com/token"), any(), eq(String.class));

            RestTemplate failing = mock(RestTemplate.class);
            SubscriptionVerificationService svc2 =
                    new SubscriptionVerificationService(
                            true,
                            "io.pinkspider.lut",
                            writeFakeServiceAccountJson().toString(),
                            new IapAppleProperties());
            svc2.setRestTemplate(failing);
            when(failing.postForObject(
                            eq("https://oauth2.googleapis.com/token"), any(), eq(String.class)))
                    .thenThrow(
                            new org.springframework.web.client.HttpServerErrorException(
                                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
            assertThatThrownBy(() -> svc2.fetchGoogleOrder("GPA.1..1"))
                    .isInstanceOf(StoreTransientException.class);

            RestTemplate empty = mock(RestTemplate.class);
            SubscriptionVerificationService svc3 =
                    new SubscriptionVerificationService(
                            true,
                            "io.pinkspider.lut",
                            writeFakeServiceAccountJson().toString(),
                            new IapAppleProperties());
            svc3.setRestTemplate(empty);
            when(empty.postForObject(
                            eq("https://oauth2.googleapis.com/token"), any(), eq(String.class)))
                    .thenReturn("{}");
            assertThatThrownBy(() -> svc3.fetchGoogleOrder("GPA.1..1"))
                    .isInstanceOf(CustomException.class)
                    .extracting("code")
                    .isEqualTo("120702");
        }

        @Test
        @DisplayName("LUT-617: parseMoney — units/nanos 합산, 둘 다 없으면 null; 주문에 이용 기간이 없으면 null")
        void google_parseMoneyAndOrderEdges() throws Exception {
            com.fasterxml.jackson.databind.ObjectMapper om =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            assertThat(
                            SubscriptionVerificationService.parseMoney(
                                    om.readTree(
                                            "{\"currencyCode\":\"USD\",\"units\":\"4\",\"nanos\":990000000}")))
                    .isEqualByComparingTo("4.99");
            assertThat(
                            SubscriptionVerificationService.parseMoney(
                                    om.readTree("{\"currencyCode\":\"KRW\"}")))
                    .isNull();
            assertThat(
                            SubscriptionVerificationService.parseMoney(
                                    om.readTree("{\"nanos\":500000000}")))
                    .isEqualByComparingTo("0.50");

            io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleOrder
                    bare =
                            SubscriptionVerificationService.parseGoogleOrder(
                                    "GPA.9",
                                    om.readTree(
                                            "{\"state\":\"REFUNDED\",\"lineItems\":[{\"productId\":\"x\"}]}"));
            assertThat(bare.orderId()).isEqualTo("GPA.9");
            assertThat(bare.state()).isEqualTo("REFUNDED");
            assertThat(bare.createTime()).isNull();
            assertThat(bare.totalAmount()).isNull();
            assertThat(bare.servicePeriodStart()).isNull();
            assertThat(bare.servicePeriodEnd()).isNull();
        }

        @Test
        @DisplayName("활성 구독을 결과로 매핑한다 — base plan/만료/자동갱신")
        void google_active_mapsResponse() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                            contains("/purchases/subscriptionsv2/tokens/token-001"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenReturn(
                            ResponseEntity.ok(
                                    "{"
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
        @DisplayName("최초 주문 + offerId 면 무료 체험으로 식별하고, 해지 상태면 자동갱신 false")
        void google_offerAndCanceled_mapped() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                            contains("/purchases/subscriptionsv2/tokens/token-001"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenReturn(
                            ResponseEntity.ok(
                                    "{\"subscriptionState\":\"SUBSCRIPTION_STATE_CANCELED\","
                                        + "\"latestOrderId\":\"GPA.3385-4794-1007-42420\",\"lineItems\":[{"
                                        + "  \"productId\":\"membership\", "
                                        + " \"expiryTime\":\"2026-10-04T00:00:00Z\", "
                                        + " \"autoRenewingPlan\":{\"autoRenewEnabled\":false}, "
                                        + " \"offerDetails\":{\"basePlanId\":\"1y\",\"offerId\":\"free-trial\"}"
                                        + "}]}"));

            SubscriptionVerificationResult result = svc.verify(androidRequest());

            assertThat(result.autoRenew()).isFalse();
            assertThat(result.trial()).isTrue();
        }

        @Test
        @DisplayName("LUT-514: 오퍼가 유지돼도 갱신 주문(GPA.xxx..N)이면 무료 체험이 아니다")
        void google_renewalWithPersistedOffer_notTrial() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            // 갱신 건: offerDetails.offerId 는 최초 구매 오퍼라 그대로 남지만 latestOrderId 에 ..N 접미사가 붙는다
            when(rest.exchange(
                            contains("/purchases/subscriptionsv2/tokens/token-001"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenReturn(
                            ResponseEntity.ok(
                                    "{\"subscriptionState\":\"SUBSCRIPTION_STATE_ACTIVE\","
                                        + "\"latestOrderId\":\"GPA.3385-4794-1007-42420..0\",\"lineItems\":[{"
                                        + "  \"productId\":\"membership\", "
                                        + " \"expiryTime\":\"2026-10-04T00:00:00Z\", "
                                        + " \"autoRenewingPlan\":{\"autoRenewEnabled\":true}, "
                                        + " \"offerDetails\":{\"basePlanId\":\"1y\",\"offerId\":\"freetrial-7d\"}"
                                        + "}]}"));

            SubscriptionVerificationResult result = svc.verify(androidRequest());

            assertThat(result.transactionId()).isEqualTo("GPA.3385-4794-1007-42420..0");
            assertThat(result.trial()).isFalse();
        }

        @Test
        @DisplayName("LUT-499: linkedPurchaseToken·latestOrderId 를 파싱해 연속성 키와 거래 ID로 싣는다")
        void google_linkedTokenAndOrderId_mapped() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                            contains("/purchases/subscriptionsv2/tokens/token-001"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenReturn(
                            ResponseEntity.ok(
                                    "{"
                                            + "\"subscriptionState\":\"SUBSCRIPTION_STATE_ACTIVE\","
                                            + "\"latestOrderId\":\"GPA.3333-4444-5555-66666\","
                                            + "\"linkedPurchaseToken\":\"token-000\","
                                            + "\"lineItems\":[{"
                                            + "  \"productId\":\"membership\","
                                            + "  \"expiryTime\":\"2027-09-04T00:00:00Z\","
                                            + "  \"autoRenewingPlan\":{\"autoRenewEnabled\":true},"
                                            + "  \"offerDetails\":{\"basePlanId\":\"1y\"}"
                                            + "}]}"));

            SubscriptionVerificationResult result = svc.verify(androidRequest());

            assertThat(result.transactionId()).isEqualTo("GPA.3333-4444-5555-66666");
            assertThat(result.linkedPurchaseToken()).isEqualTo("token-000");
            // Google 은 실결제가를 주지 않는다
            assertThat(result.priceAmount()).isNull();

            GoogleSubscriptionState state = svc.fetchGoogleSubscription("token-001");
            assertThat(state.linkedPurchaseToken()).isEqualTo("token-000");
            assertThat(state.latestOrderId()).isEqualTo("GPA.3333-4444-5555-66666");
        }

        @Test
        @DisplayName("LUT-499: 응답에 연속성 키·주문 ID 가 없으면 null 로 둔다")
        void google_missingLinkedFields_null() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                            contains("/purchases/subscriptionsv2/tokens/token-001"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenReturn(
                            ResponseEntity.ok(
                                    "{"
                                            + "\"subscriptionState\":\"SUBSCRIPTION_STATE_ACTIVE\","
                                            + "\"lineItems\":[{\"productId\":\"membership\","
                                            + "\"expiryTime\":\"2027-09-04T00:00:00Z\","
                                            + "\"autoRenewingPlan\":{\"autoRenewEnabled\":true},"
                                            + "\"offerDetails\":{\"basePlanId\":\"1y\"}}]}"));

            SubscriptionVerificationResult result = svc.verify(androidRequest());

            assertThat(result.transactionId()).isNull();
            assertThat(result.linkedPurchaseToken()).isNull();
        }

        @Test
        @DisplayName("플랜 변경으로 line item이 복수면 만료가 가장 늦은 항목을 쓴다")
        void google_multipleLineItems_usesLatestExpiry() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            SubscriptionVerificationService svc = serviceWithGoogle(rest);
            when(rest.exchange(
                            contains("/purchases/subscriptionsv2/tokens/token-001"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenReturn(
                            ResponseEntity.ok(
                                    "{\"subscriptionState\":\"SUBSCRIPTION_STATE_ACTIVE\","
                                        + "\"lineItems\":["
                                        + "{\"productId\":\"membership\",\"expiryTime\":\"2026-10-04T00:00:00Z\","
                                        + " \"offerDetails\":{\"basePlanId\":\"1m\"}},"
                                        + "{\"productId\":\"membership\",\"expiryTime\":\"2027-09-04T00:00:00Z\","
                                        + " \"autoRenewingPlan\":{\"autoRenewEnabled\":true},"
                                        + " \"offerDetails\":{\"basePlanId\":\"1y\"}}]}"));

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
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenReturn(
                            ResponseEntity.ok(
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
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenReturn(
                            ResponseEntity.ok(
                                    "{"
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

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("LUT-493: 토큰 교환 JWT의 aud 는 배열이 아닌 문자열이어야 한다")
        void google_assertionAudience_isSingleString() throws Exception {
            RestTemplate rest = mock(RestTemplate.class);
            ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> tokenRequest =
                    ArgumentCaptor.forClass(HttpEntity.class);
            SubscriptionVerificationService svc =
                    new SubscriptionVerificationService(
                            true,
                            "io.pinkspider.lut",
                            writeFakeServiceAccountJson().toString(),
                            new IapAppleProperties());
            svc.setRestTemplate(rest);
            when(rest.postForObject(
                            eq("https://oauth2.googleapis.com/token"),
                            tokenRequest.capture(),
                            eq(String.class)))
                    .thenReturn("{\"access_token\":\"fake-token\"}");
            when(rest.exchange(
                            contains("/purchases/subscriptionsv2/tokens/token-001"),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenReturn(
                            ResponseEntity.ok(
                                    "{"
                                            + "\"subscriptionState\":\"SUBSCRIPTION_STATE_ACTIVE\","
                                            + "\"startTime\":\"2026-09-04T00:00:00Z\","
                                            + "\"lineItems\":[{"
                                            + "  \"productId\":\"membership\","
                                            + "  \"expiryTime\":\"2027-09-04T00:00:00Z\","
                                            + "  \"autoRenewingPlan\":{\"autoRenewEnabled\":true},"
                                            + "  \"offerDetails\":{\"basePlanId\":\"1y\"}"
                                            + "}]}"));

            svc.verify(androidRequest());

            // jjwt 0.12 의 audience().add() 는 aud 를 배열로 직렬화한다 — 구글 OAuth2 토큰 엔드포인트는
            // 문자열 aud 만 허용하므로(invalid_grant: Failed audience check) single() 이어야 한다
            String assertion = tokenRequest.getValue().getBody().getFirst("assertion");
            String payloadJson =
                    new String(
                            Base64.getUrlDecoder().decode(assertion.split("\\.")[1]),
                            StandardCharsets.UTF_8);
            JsonNode aud = new ObjectMapper().readTree(payloadJson).path("aud");
            assertThat(aud.isTextual()).isTrue();
            assertThat(aud.asText()).isEqualTo("https://oauth2.googleapis.com/token");
        }

        /** RSA 키를 즉석 생성해 서비스계정 JSON 파일을 만든다 (IapVerificationServiceTest 패턴) */
        private Path writeFakeServiceAccountJson() throws Exception {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            String privateKeyPem =
                    "-----BEGIN PRIVATE KEY-----\n"
                            + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                            + "\n-----END PRIVATE KEY-----\n";

            Path serviceAccountFile =
                    tempDir.resolve("service-account-" + System.nanoTime() + ".json");
            String json =
                    "{\"client_email\":\"svc@example.iam.gserviceaccount.com\",\"private_key\":\""
                            + privateKeyPem.replace("\n", "\\n")
                            + "\"}";
            Files.writeString(serviceAccountFile, json);
            return serviceAccountFile;
        }
    }
}
