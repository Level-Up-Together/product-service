package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.IapVerificationResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@DisplayName("IapVerificationService 테스트 (LUT-354, LUT-401)")
class IapVerificationServiceTest {

    private static final String APPLE_URL = "https://apple.example/verifyReceipt";
    private static final String APPLE_SANDBOX_URL = "https://apple-sandbox.example/verifyReceipt";

    @TempDir
    Path tempDir;

    private IapVerificationService service(boolean enabled) {
        // Apple 자격증명 미설정 — App Store Server API 가격 보강은 내부에서 best-effort 로 실패해
        // withoutPrice 로 폴백한다(가격 캡처 자체는 별도 테스트에서 검증).
        return new IapVerificationService(
            enabled, APPLE_URL, APPLE_SANDBOX_URL, "io.pinkspider.lut", "", new IapAppleProperties());
    }

    private DiamondBundlePurchaseRequest iosRequest() {
        return DiamondBundlePurchaseRequest.builder()
            .platform("ios")
            .storeProductId("pink_100")
            .transactionId("tx-001")
            .receipt("base64-receipt")
            .build();
    }

    private DiamondBundlePurchaseRequest androidRequest() {
        return DiamondBundlePurchaseRequest.builder()
            .platform("android")
            .storeProductId("pink_100")
            .purchaseToken("token-001")
            .build();
    }

    @Nested
    @DisplayName("검증 비활성 (dev 기본)")
    class DisabledTest {

        @Test
        @DisplayName("ios는 transactionId를 신뢰해 반환한다")
        void disabled_ios_trustsTransactionId() {
            assertThat(service(false).verify(iosRequest()).transactionId()).isEqualTo("tx-001");
        }

        @Test
        @DisplayName("android는 purchaseToken을 신뢰해 반환한다")
        void disabled_android_trustsPurchaseToken() {
            assertThat(service(false).verify(androidRequest()).transactionId()).isEqualTo("token-001");
        }

        @Test
        @DisplayName("트랜잭션 식별자가 없으면 비활성이라도 예외")
        void disabled_missingIdentifier_throws() {
            DiamondBundlePurchaseRequest request = DiamondBundlePurchaseRequest.builder()
                .platform("android")
                .storeProductId("pink_100")
                .build();

            assertThatThrownBy(() -> service(false).verify(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.receipt_required");
        }
    }

    @Nested
    @DisplayName("Apple 검증")
    class AppleTest {

        @Test
        @DisplayName("status 0 + 상품·트랜잭션 일치 시 트랜잭션 ID를 반환한다")
        void apple_valid_returnsTransactionId() {
            IapVerificationService svc = service(true);
            RestTemplate rest = mock(RestTemplate.class);
            svc.setRestTemplate(rest);
            when(rest.postForObject(eq(APPLE_URL), any(), eq(String.class)))
                .thenReturn("{\"status\":0,\"receipt\":{\"in_app\":[" +
                    "{\"product_id\":\"pink_100\",\"transaction_id\":\"tx-001\"}]}}");

            assertThat(svc.verify(iosRequest()).transactionId()).isEqualTo("tx-001");
        }

        @Test
        @DisplayName("21007이면 샌드박스로 재시도한다 (심사 표준 흐름)")
        void apple_sandboxReceipt_retriesSandbox() {
            IapVerificationService svc = service(true);
            RestTemplate rest = mock(RestTemplate.class);
            svc.setRestTemplate(rest);
            when(rest.postForObject(eq(APPLE_URL), any(), eq(String.class)))
                .thenReturn("{\"status\":21007}");
            when(rest.postForObject(eq(APPLE_SANDBOX_URL), any(), eq(String.class)))
                .thenReturn("{\"status\":0,\"receipt\":{\"in_app\":[" +
                    "{\"product_id\":\"pink_100\",\"transaction_id\":\"tx-001\"}]}}");

            assertThat(svc.verify(iosRequest()).transactionId()).isEqualTo("tx-001");
        }

        @Test
        @DisplayName("status가 0이 아니면 검증 실패")
        void apple_invalidStatus_throws() {
            IapVerificationService svc = service(true);
            RestTemplate rest = mock(RestTemplate.class);
            svc.setRestTemplate(rest);
            when(rest.postForObject(eq(APPLE_URL), any(), eq(String.class)))
                .thenReturn("{\"status\":21002}");

            assertThatThrownBy(() -> svc.verify(iosRequest()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.verification_failed");
        }

        @Test
        @DisplayName("영수증에 요청 상품·트랜잭션이 없으면 불일치 예외")
        void apple_noMatchingEntry_throws() {
            IapVerificationService svc = service(true);
            RestTemplate rest = mock(RestTemplate.class);
            svc.setRestTemplate(rest);
            when(rest.postForObject(eq(APPLE_URL), any(), eq(String.class)))
                .thenReturn("{\"status\":0,\"receipt\":{\"in_app\":[" +
                    "{\"product_id\":\"other_item\",\"transaction_id\":\"tx-999\"}]}}");

            assertThatThrownBy(() -> svc.verify(iosRequest()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.product_mismatch");
        }

        @Test
        @DisplayName("검증 활성 상태에서 ios transactionId 가 없으면 예외")
        void apple_enabledWithoutTransactionId_throws() {
            DiamondBundlePurchaseRequest request = DiamondBundlePurchaseRequest.builder()
                .platform("ios")
                .storeProductId("pink_100")
                .receipt("base64-receipt")
                .build();

            assertThatThrownBy(() -> service(true).verify(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.receipt_required");
        }

        // LUT-498: react-native-iap v16(StoreKit 2)은 legacy 영수증을 못 주는 경우가 있어 RN 이
        // transaction_id 만 보낸다. 예전엔 이를 120701 로 거절해 iOS 다이아 결제가 항상 실패했다.
        @Nested
        @DisplayName("LUT-498: 영수증 없는 iOS 요청은 App Store Server API 로 검증한다")
        class AppleServerApiTest {

            private DiamondBundlePurchaseRequest receiptlessRequest() {
                return DiamondBundlePurchaseRequest.builder()
                    .platform("ios")
                    .storeProductId("pink_100")
                    .transactionId("tx-001")
                    .build();
            }

            private JWSTransactionDecodedPayload payload() {
                return new JWSTransactionDecodedPayload()
                    .productId("pink_100")
                    .transactionId("tx-001")
                    .price(1990L)
                    .currency("USD");
            }

            @Test
            @DisplayName("트랜잭션 조회·상품 일치 시 transactionId 와 가격/통화를 반환한다 (verifyReceipt 미호출)")
            void receiptless_valid_returnsWithPrice() {
                IapVerificationService svc = spy(service(true));
                RestTemplate rest = mock(RestTemplate.class);
                svc.setRestTemplate(rest);
                doReturn(payload()).when(svc).fetchAppleTransaction("tx-001");

                IapVerificationResult result = svc.verify(receiptlessRequest());

                assertThat(result.transactionId()).isEqualTo("tx-001");
                assertThat(result.priceAmount()).isEqualByComparingTo("1.99");
                assertThat(result.priceCurrency()).isEqualTo("USD");
                verify(rest, never()).postForObject(any(String.class), any(), eq(String.class));
            }

            @Test
            @DisplayName("트랜잭션의 상품이 요청과 다르면 120703")
            void receiptless_productMismatch_throws() {
                IapVerificationService svc = spy(service(true));
                doReturn(payload().productId("pink_550")).when(svc).fetchAppleTransaction("tx-001");

                assertThatThrownBy(() -> svc.verify(receiptlessRequest()))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("error.iap.product_mismatch");
            }

            @Test
            @DisplayName("환불(revocationDate)된 트랜잭션은 120702")
            void receiptless_revoked_throws() {
                IapVerificationService svc = spy(service(true));
                doReturn(payload().revocationDate(1_700_000_000_000L))
                    .when(svc).fetchAppleTransaction("tx-001");

                assertThatThrownBy(() -> svc.verify(receiptlessRequest()))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("error.iap.verification_failed");
            }

            @Test
            @DisplayName("Server API 조회가 prod·sandbox 모두 실패하면 120702 (자격증명 미설정 환경)")
            void receiptless_apiFailure_throws() {
                assertThatThrownBy(() -> service(true).verify(receiptlessRequest()))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("error.iap.verification_failed");
            }

            @Test
            @DisplayName("영수증이 있으면 기존 verifyReceipt 경로를 그대로 탄다")
            void withReceipt_usesLegacyPath() {
                IapVerificationService svc = spy(service(true));
                RestTemplate rest = mock(RestTemplate.class);
                svc.setRestTemplate(rest);
                when(rest.postForObject(eq(APPLE_URL), any(), eq(String.class)))
                    .thenReturn("{\"status\":0,\"receipt\":{\"in_app\":[" +
                        "{\"product_id\":\"pink_100\",\"transaction_id\":\"tx-001\"}]}}");

                assertThat(svc.verify(iosRequest()).transactionId()).isEqualTo("tx-001");
                verify(svc, never()).fetchAppleTransaction(any());
            }
        }

        @Test
        @DisplayName("LUT-401: App Store Server API 자격증명이 없으면 가격 없이(null) 구매를 그대로 진행한다")
        void apple_priceCapture_missingCredentials_fallsBackWithoutPrice() {
            IapVerificationService svc = service(true);
            RestTemplate rest = mock(RestTemplate.class);
            svc.setRestTemplate(rest);
            when(rest.postForObject(eq(APPLE_URL), any(), eq(String.class)))
                .thenReturn("{\"status\":0,\"receipt\":{\"in_app\":[" +
                    "{\"product_id\":\"pink_100\",\"transaction_id\":\"tx-001\"}]}}");

            IapVerificationResult result = svc.verify(iosRequest());

            assertThat(result.transactionId()).isEqualTo("tx-001");
            assertThat(result.priceAmount()).isNull();
            assertThat(result.priceCurrency()).isNull();
        }
    }

    @Nested
    @DisplayName("가격 변환 (LUT-401)")
    class PriceConversionTest {

        @Test
        @DisplayName("Apple 밀리유닛을 소수 가격으로 변환한다")
        void applePriceToDecimal_convertsMilliunits() {
            assertThat(IapVerificationService.applePriceToDecimal(1990L))
                .isEqualByComparingTo(new BigDecimal("1.990"));
        }

        @Test
        @DisplayName("Apple 가격이 null이면 null을 반환한다")
        void applePriceToDecimal_null_returnsNull() {
            assertThat(IapVerificationService.applePriceToDecimal(null)).isNull();
        }

        @Test
        @DisplayName("Google 마이크로유닛 문자열을 소수 가격으로 변환한다")
        void googleMicrosToDecimal_convertsMicros() {
            assertThat(IapVerificationService.googleMicrosToDecimal("1990000"))
                .isEqualByComparingTo(new BigDecimal("1.990000"));
        }
    }

    @Nested
    @DisplayName("Google 검증")
    class GoogleTest {

        @Test
        @DisplayName("서비스 계정 설정이 없으면 검증 실패로 처리된다")
        void google_missingServiceAccount_throws() {
            // service-account-json 미설정("") — 파일 로드 실패 → verification_failed
            assertThatThrownBy(() -> service(true).verify(androidRequest()))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.verification_failed");
        }

        @Test
        @DisplayName("LUT-401: 구매 완료 상태면 응답에 포함된 실제 결제 가격/통화를 파싱한다")
        void google_valid_parsesPrice() throws Exception {
            Path serviceAccountFile = writeFakeServiceAccountJson();
            IapVerificationService svc = new IapVerificationService(
                true, APPLE_URL, APPLE_SANDBOX_URL, "io.pinkspider.lut",
                serviceAccountFile.toString(), new IapAppleProperties());
            RestTemplate rest = mock(RestTemplate.class);
            svc.setRestTemplate(rest);

            when(rest.postForObject(eq("https://oauth2.googleapis.com/token"), any(), eq(String.class)))
                .thenReturn("{\"access_token\":\"fake-token\"}");
            when(rest.exchange(
                    contains("/purchases/products/pink_100/tokens/token-001"),
                    eq(org.springframework.http.HttpMethod.GET),
                    any(),
                    eq(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(
                    "{\"purchaseState\":0,\"priceAmountMicros\":\"1990000\",\"priceCurrencyCode\":\"USD\"}"));

            IapVerificationResult result = svc.verify(androidRequest());

            assertThat(result.transactionId()).isEqualTo("token-001");
            assertThat(result.priceAmount()).isEqualByComparingTo(new BigDecimal("1.990000"));
            assertThat(result.priceCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("LUT-401: 가격 필드가 없으면 가격 없이(null) 반환한다")
        void google_missingPriceFields_returnsWithoutPrice() throws Exception {
            Path serviceAccountFile = writeFakeServiceAccountJson();
            IapVerificationService svc = new IapVerificationService(
                true, APPLE_URL, APPLE_SANDBOX_URL, "io.pinkspider.lut",
                serviceAccountFile.toString(), new IapAppleProperties());
            RestTemplate rest = mock(RestTemplate.class);
            svc.setRestTemplate(rest);

            when(rest.postForObject(eq("https://oauth2.googleapis.com/token"), any(), eq(String.class)))
                .thenReturn("{\"access_token\":\"fake-token\"}");
            when(rest.exchange(
                    contains("/purchases/products/pink_100/tokens/token-001"),
                    eq(org.springframework.http.HttpMethod.GET),
                    any(),
                    eq(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok("{\"purchaseState\":0}"));

            IapVerificationResult result = svc.verify(androidRequest());

            assertThat(result.transactionId()).isEqualTo("token-001");
            assertThat(result.priceAmount()).isNull();
            assertThat(result.priceCurrency()).isNull();
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("LUT-493: 토큰 교환 JWT의 aud 는 배열이 아닌 문자열이어야 한다")
        void google_assertionAudience_isSingleString() throws Exception {
            Path serviceAccountFile = writeFakeServiceAccountJson();
            IapVerificationService svc = new IapVerificationService(
                true, APPLE_URL, APPLE_SANDBOX_URL, "io.pinkspider.lut",
                serviceAccountFile.toString(), new IapAppleProperties());
            RestTemplate rest = mock(RestTemplate.class);
            svc.setRestTemplate(rest);

            ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> tokenRequest =
                ArgumentCaptor.forClass(HttpEntity.class);
            when(rest.postForObject(
                    eq("https://oauth2.googleapis.com/token"), tokenRequest.capture(), eq(String.class)))
                .thenReturn("{\"access_token\":\"fake-token\"}");
            when(rest.exchange(
                    contains("/purchases/products/pink_100/tokens/token-001"),
                    eq(org.springframework.http.HttpMethod.GET),
                    any(),
                    eq(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok("{\"purchaseState\":0}"));

            svc.verify(androidRequest());

            // jjwt 0.12 의 audience().add() 는 aud 를 배열로 직렬화한다 — 구글 OAuth2 토큰 엔드포인트는
            // 문자열 aud 만 허용하므로(invalid_grant: Failed audience check) single() 이어야 한다
            String assertion = tokenRequest.getValue().getBody().getFirst("assertion");
            String payloadJson = new String(
                Base64.getUrlDecoder().decode(assertion.split("\\.")[1]), StandardCharsets.UTF_8);
            JsonNode aud = new ObjectMapper().readTree(payloadJson).path("aud");
            assertThat(aud.isTextual()).isTrue();
            assertThat(aud.asText()).isEqualTo("https://oauth2.googleapis.com/token");
        }

        /** RSA 키를 즉석 생성해 parsePrivateKey 가 소비할 수 있는 형태의 임시 서비스계정 JSON을 만든다 */
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
