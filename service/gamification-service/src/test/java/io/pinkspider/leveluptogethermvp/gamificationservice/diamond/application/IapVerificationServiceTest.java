package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondBundlePurchaseRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

@DisplayName("IapVerificationService 테스트 (LUT-354)")
class IapVerificationServiceTest {

    private static final String APPLE_URL = "https://apple.example/verifyReceipt";
    private static final String APPLE_SANDBOX_URL = "https://apple-sandbox.example/verifyReceipt";

    private IapVerificationService service(boolean enabled) {
        return new IapVerificationService(enabled, APPLE_URL, APPLE_SANDBOX_URL, "io.pinkspider.lut", "");
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
            assertThat(service(false).verify(iosRequest())).isEqualTo("tx-001");
        }

        @Test
        @DisplayName("android는 purchaseToken을 신뢰해 반환한다")
        void disabled_android_trustsPurchaseToken() {
            assertThat(service(false).verify(androidRequest())).isEqualTo("token-001");
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

            assertThat(svc.verify(iosRequest())).isEqualTo("tx-001");
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

            assertThat(svc.verify(iosRequest())).isEqualTo("tx-001");
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
        @DisplayName("검증 활성 상태에서 ios 영수증이 없으면 예외")
        void apple_enabledWithoutReceipt_throws() {
            DiamondBundlePurchaseRequest request = DiamondBundlePurchaseRequest.builder()
                .platform("ios")
                .storeProductId("pink_100")
                .transactionId("tx-001")
                .build();

            assertThatThrownBy(() -> service(true).verify(request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.iap.receipt_required");
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
    }
}
