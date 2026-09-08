package io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.security.OAuth2Properties;
import io.pinkspider.leveluptogethermvp.userservice.mypage.application.MyPageService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppleWebhookService 테스트 (LUT-476)")
class AppleWebhookServiceTest {

    private static final String KID = "test-kid";
    private static final String APPLE_SUB = "001234.abcdef";

    @Mock
    private UserRepository userRepository;

    @Mock
    private MyPageService myPageService;

    private OAuth2Properties oAuth2Properties;
    private AppleWebhookService appleWebhookService;
    private RSAKey rsaKey;

    @BeforeEach
    void setUp() throws Exception {
        oAuth2Properties = new OAuth2Properties();
        appleWebhookService = new AppleWebhookService(
            oAuth2Properties, userRepository, myPageService, new ObjectMapper());

        // 실제 Apple JWKS 원격 조회 대신 테스트 키를 캐시에 주입 (서명 검증 경로는 그대로 탄다)
        rsaKey = new RSAKeyGenerator(2048).keyID(KID).generate();
        injectJwksCache(rsaKey.toPublicJWK());
    }

    @SuppressWarnings("unchecked")
    private void injectJwksCache(RSAKey publicKey) throws Exception {
        Field field = AppleWebhookService.class.getDeclaredField("jwksCache");
        field.setAccessible(true);
        ((Map<String, RSAKey>) field.get(appleWebhookService)).put(KID, publicKey);
    }

    private String signedPayload(String issuer, String eventType, String sub) throws Exception {
        String events = new ObjectMapper().writeValueAsString(Map.of(
            "type", eventType,
            "sub", sub,
            "event_time", System.currentTimeMillis()));
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience("com.level-up-together.dev")
            .issueTime(new Date())
            .claim("events", events)
            .build();
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).type(JOSEObjectType.JWT).build(),
            claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    private Users appleUser() {
        return Users.builder()
            .id("user-1").email("e").nickname("n")
            .provider("apple").providerUserId(APPLE_SUB)
            .build();
    }

    @Test
    @DisplayName("consent-revoked 이벤트 수신 시 매핑된 계정을 탈퇴 처리한다")
    void consentRevoked_withdrawsMappedUser() throws Exception {
        when(userRepository.findActiveByProviderAndProviderUserId("apple", APPLE_SUB))
            .thenReturn(Optional.of(appleUser()));

        appleWebhookService.handleNotification(
            signedPayload(AppleWebhookService.APPLE_ISSUER, "consent-revoked", APPLE_SUB));

        verify(myPageService).withdrawUser("user-1");
    }

    @Test
    @DisplayName("account-delete 이벤트 수신 시 매핑된 계정을 탈퇴 처리한다")
    void accountDelete_withdrawsMappedUser() throws Exception {
        when(userRepository.findActiveByProviderAndProviderUserId("apple", APPLE_SUB))
            .thenReturn(Optional.of(appleUser()));

        appleWebhookService.handleNotification(
            signedPayload(AppleWebhookService.APPLE_ISSUER, "account-delete", APPLE_SUB));

        verify(myPageService).withdrawUser("user-1");
    }

    @Test
    @DisplayName("email-disabled 등 그 외 이벤트는 탈퇴 처리하지 않는다")
    void otherEvents_doNotWithdraw() throws Exception {
        appleWebhookService.handleNotification(
            signedPayload(AppleWebhookService.APPLE_ISSUER, "email-disabled", APPLE_SUB));

        verify(myPageService, never()).withdrawUser(anyString());
    }

    @Test
    @DisplayName("매핑되는 활성 사용자가 없으면(백필 전/기탈퇴) 예외 없이 지나간다")
    void unmappedSub_isNoOp() throws Exception {
        when(userRepository.findActiveByProviderAndProviderUserId("apple", APPLE_SUB))
            .thenReturn(Optional.empty());

        assertThatCode(() -> appleWebhookService.handleNotification(
            signedPayload(AppleWebhookService.APPLE_ISSUER, "consent-revoked", APPLE_SUB)))
            .doesNotThrowAnyException();
        verify(myPageService, never()).withdrawUser(anyString());
    }

    @Test
    @DisplayName("발급자가 Apple 이 아니면 거부한다")
    void invalidIssuer_isRejected() throws Exception {
        String payload = signedPayload("https://evil.example.com", "consent-revoked", APPLE_SUB);

        assertThatThrownBy(() -> appleWebhookService.handleNotification(payload))
            .isInstanceOf(CustomException.class)
            .hasMessageContaining("error.apple.webhook.invalid_issuer");
    }

    @Test
    @DisplayName("다른 키로 서명된 페이로드는 거부한다")
    void wrongSignature_isRejected() throws Exception {
        // 캐시에 없는 별도 키로 서명 → 검증 실패
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID(KID).generate();
        RSAKey original = rsaKey;
        rsaKey = otherKey;
        String payload = signedPayload(AppleWebhookService.APPLE_ISSUER, "consent-revoked", APPLE_SUB);
        rsaKey = original;

        assertThatThrownBy(() -> appleWebhookService.handleNotification(payload))
            .isInstanceOf(CustomException.class)
            .hasMessageContaining("error.apple.webhook.invalid_signature");
    }

    @Test
    @DisplayName("audience 허용 목록이 설정되면 불일치 페이로드를 거부한다")
    void audienceMismatch_isRejectedWhenConfigured() throws Exception {
        oAuth2Properties.getAppleWebhook().setAudiences(List.of("com.level-up-together.prod"));

        String payload = signedPayload(AppleWebhookService.APPLE_ISSUER, "consent-revoked", APPLE_SUB);

        assertThatThrownBy(() -> appleWebhookService.handleNotification(payload))
            .isInstanceOf(CustomException.class)
            .hasMessageContaining("error.apple.webhook.invalid_audience");
    }

    @Test
    @DisplayName("audience 허용 목록이 비어 있으면 검증을 생략한다 (kakao appId 관례)")
    void emptyAudienceConfig_skipsCheck() throws Exception {
        when(userRepository.findActiveByProviderAndProviderUserId("apple", APPLE_SUB))
            .thenReturn(Optional.of(appleUser()));

        assertThatCode(() -> appleWebhookService.handleNotification(
            signedPayload(AppleWebhookService.APPLE_ISSUER, "consent-revoked", APPLE_SUB)))
            .doesNotThrowAnyException();
    }
}
