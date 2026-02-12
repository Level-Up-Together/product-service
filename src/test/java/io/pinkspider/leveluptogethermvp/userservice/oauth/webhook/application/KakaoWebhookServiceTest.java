package io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.security.OAuth2Properties;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.MultiDeviceTokenService;
import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.dto.KakaoUnlinkWebhookRequest;
import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.enums.KakaoAccountEventType;
import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.enums.KakaoUnlinkReferrerType;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KakaoWebhookServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MultiDeviceTokenService tokenService;

    private OAuth2Properties oAuth2Properties;
    private ObjectMapper objectMapper;
    private KakaoWebhookService kakaoWebhookService;

    @BeforeEach
    void setUp() {
        oAuth2Properties = new OAuth2Properties();
        OAuth2Properties.KakaoWebhook kakaoWebhook = new OAuth2Properties.KakaoWebhook();
        kakaoWebhook.setAdminKey("test-admin-key");
        kakaoWebhook.setRestApiKey("test-rest-api-key");
        kakaoWebhook.setAppId("123456");
        oAuth2Properties.setKakaoWebhook(kakaoWebhook);

        objectMapper = new ObjectMapper();

        kakaoWebhookService = new KakaoWebhookService(
            oAuth2Properties,
            userRepository,
            tokenService,
            objectMapper
        );
    }

    @Nested
    @DisplayName("연결 해제 웹훅 테스트")
    class UnlinkWebhookTest {

        @Test
        @DisplayName("유효한 어드민 키로 연결 해제 웹훅 처리 성공")
        void handleUnlinkWebhook_success() {
            // given
            String authorization = "KakaoAK test-admin-key";
            KakaoUnlinkWebhookRequest request = KakaoUnlinkWebhookRequest.builder()
                .appId("123456")
                .userId("987654321")
                .referrerType("UNLINK_FROM_APPS")
                .build();

            // when & then
            assertDoesNotThrow(() -> kakaoWebhookService.handleUnlinkWebhook(authorization, request));
        }

        @Test
        @DisplayName("잘못된 Authorization 헤더 형식으로 실패")
        void handleUnlinkWebhook_invalidAuthorizationFormat() {
            // given
            String authorization = "Bearer invalid-format";
            KakaoUnlinkWebhookRequest request = KakaoUnlinkWebhookRequest.builder()
                .appId("123456")
                .userId("987654321")
                .referrerType("UNLINK_FROM_APPS")
                .build();

            // when & then
            CustomException exception = assertThrows(CustomException.class,
                () -> kakaoWebhookService.handleUnlinkWebhook(authorization, request));
            assertEquals("000720", exception.getCode());
        }

        @Test
        @DisplayName("잘못된 어드민 키로 실패")
        void handleUnlinkWebhook_invalidAdminKey() {
            // given
            String authorization = "KakaoAK wrong-admin-key";
            KakaoUnlinkWebhookRequest request = KakaoUnlinkWebhookRequest.builder()
                .appId("123456")
                .userId("987654321")
                .referrerType("UNLINK_FROM_APPS")
                .build();

            // when & then
            CustomException exception = assertThrows(CustomException.class,
                () -> kakaoWebhookService.handleUnlinkWebhook(authorization, request));
            assertEquals("000720", exception.getCode());
        }

        @Test
        @DisplayName("잘못된 앱 ID로 실패")
        void handleUnlinkWebhook_invalidAppId() {
            // given
            String authorization = "KakaoAK test-admin-key";
            KakaoUnlinkWebhookRequest request = KakaoUnlinkWebhookRequest.builder()
                .appId("wrong-app-id")
                .userId("987654321")
                .referrerType("UNLINK_FROM_APPS")
                .build();

            // when & then
            CustomException exception = assertThrows(CustomException.class,
                () -> kakaoWebhookService.handleUnlinkWebhook(authorization, request));
            assertEquals("000720", exception.getCode());
        }
    }

    @Nested
    @DisplayName("계정 상태 변경 웹훅 테스트")
    class AccountStatusWebhookTest {

        @Test
        @DisplayName("잘못된 JWT 형식으로 검증 실패")
        void handleAccountStatusWebhook_invalidJwtFormat() {
            // given
            String invalidSetToken = "invalid.jwt";

            // when & then
            KakaoWebhookService.SetValidationException exception = assertThrows(
                KakaoWebhookService.SetValidationException.class,
                () -> kakaoWebhookService.handleAccountStatusWebhook(invalidSetToken)
            );
            assertEquals("invalid_request", exception.getErrorCode());
        }

        @Test
        @DisplayName("잘못된 토큰 타입으로 검증 실패")
        void handleAccountStatusWebhook_invalidTokenType() {
            // given - typ이 jwt인 경우 (secevent+jwt가 아님)
            // Header: {"kid":"test","typ":"jwt","alg":"RS256"}
            String invalidTypeToken = "eyJraWQiOiJ0ZXN0IiwidHlwIjoiand0IiwiYWxnIjoiUlMyNTYifQ." +
                "eyJhdWQiOiJ0ZXN0IiwiYmxhIjoiYmxhIn0." +
                "signature";

            // when & then
            KakaoWebhookService.SetValidationException exception = assertThrows(
                KakaoWebhookService.SetValidationException.class,
                () -> kakaoWebhookService.handleAccountStatusWebhook(invalidTypeToken)
            );
            assertEquals("invalid_request", exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Enum 변환 테스트")
    class EnumConversionTest {

        @Test
        @DisplayName("KakaoUnlinkReferrerType 변환 테스트")
        void testUnlinkReferrerTypeConversion() {
            assertEquals(KakaoUnlinkReferrerType.ACCOUNT_DELETE,
                KakaoUnlinkReferrerType.fromValue("ACCOUNT_DELETE"));
            assertEquals(KakaoUnlinkReferrerType.UNLINK_FROM_APPS,
                KakaoUnlinkReferrerType.fromValue("UNLINK_FROM_APPS"));
            assertEquals(KakaoUnlinkReferrerType.FORCED_UNLINK_BY_ADMIN,
                KakaoUnlinkReferrerType.fromValue("FORCED_UNLINK_BY_ADMIN"));
            assertEquals(KakaoUnlinkReferrerType.UNKNOWN,
                KakaoUnlinkReferrerType.fromValue("INVALID_TYPE"));
            assertEquals(KakaoUnlinkReferrerType.UNKNOWN,
                KakaoUnlinkReferrerType.fromValue(null));
        }

        @Test
        @DisplayName("KakaoAccountEventType 변환 테스트")
        void testAccountEventTypeConversion() {
            assertEquals(KakaoAccountEventType.USER_UNLINKED,
                KakaoAccountEventType.fromUri("https://schemas.kakao.com/events/oauth/user-unlinked"));
            assertEquals(KakaoAccountEventType.ACCOUNT_DISABLED,
                KakaoAccountEventType.fromUri("https://schemas.openid.net/secevent/risc/event-type/account-disabled"));
            assertEquals(KakaoAccountEventType.CREDENTIAL_CHANGE,
                KakaoAccountEventType.fromUri("https://schemas.openid.net/secevent/caep/event-type/credential-change"));

            // 알 수 없는 URI
            assertEquals(null, KakaoAccountEventType.fromUri("https://unknown.uri"));
            assertEquals(null, KakaoAccountEventType.fromUri(null));
        }

        @Test
        @DisplayName("KakaoAccountEventType 속성 테스트")
        void testAccountEventTypeProperties() {
            KakaoAccountEventType userUnlinked = KakaoAccountEventType.USER_UNLINKED;
            assertEquals("https://schemas.kakao.com/events/oauth/user-unlinked", userUnlinked.getUri());
            assertEquals("OAUTH", userUnlinked.getCategory());
            assertEquals("사용자 앱 연결 해제", userUnlinked.getDescription());
        }
    }
}
