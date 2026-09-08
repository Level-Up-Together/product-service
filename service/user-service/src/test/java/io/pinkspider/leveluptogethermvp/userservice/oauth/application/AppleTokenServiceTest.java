package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import io.pinkspider.global.security.OAuth2Properties;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.apple.AppleAuthFeignClient;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppleTokenService 테스트 (LUT-477)")
class AppleTokenServiceTest {

    @Mock
    private AppleAuthFeignClient appleAuthFeignClient;

    private OAuth2Properties oAuth2Properties;
    private AppleTokenService appleTokenService;
    private ECPublicKey testPublicKey;

    @BeforeEach
    void setUp() throws Exception {
        // 테스트 전용 P-256 키쌍 (실제 SIWA 키와 무관)
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        testPublicKey = (ECPublicKey) keyPair.getPublic();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";

        oAuth2Properties = new OAuth2Properties();
        OAuth2Properties.Apple apple = new OAuth2Properties.Apple();
        apple.setTeamId("TEAM123456");
        apple.setKeyId("KEY9876543");
        apple.setPrivateKey(pem);
        oAuth2Properties.setApple(apple);

        appleTokenService = new AppleTokenService(oAuth2Properties, appleAuthFeignClient);
    }

    @Test
    @DisplayName("client_secret 은 ES256 서명 JWT — iss=팀ID, sub=clientId, kid=키ID")
    void generateClientSecret_signsValidJwt() throws Exception {
        String secret = appleTokenService.generateClientSecret("io.pinkspider.lut");

        SignedJWT jwt = SignedJWT.parse(secret);
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEY9876543");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("TEAM123456");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("io.pinkspider.lut");
        assertThat(jwt.getJWTClaimsSet().getAudience())
            .containsExactly("https://appleid.apple.com");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isInTheFuture();
        assertThat(jwt.verify(new ECDSAVerifier(testPublicKey))).isTrue();
    }

    @Test
    @DisplayName("code 교환 성공 시 refresh_token 을 반환한다")
    void exchangeRefreshToken_success() {
        when(appleAuthFeignClient.exchangeToken(
            eq("io.pinkspider.lut"), anyString(), eq("authorization_code"),
            eq("auth-code"), isNull()))
            .thenReturn(Map.of("access_token", "at", "refresh_token", "rt-123"));

        Optional<String> result =
            appleTokenService.exchangeRefreshToken("auth-code", "io.pinkspider.lut", null);

        assertThat(result).contains("rt-123");
    }

    @Test
    @DisplayName("웹/Android code 교환은 redirect_uri 를 함께 전달한다")
    void exchangeRefreshToken_passesRedirectUri() {
        when(appleAuthFeignClient.exchangeToken(
            anyString(), anyString(), anyString(), anyString(),
            eq("https://dev.level-up-together.com/oauth/callback/apple")))
            .thenReturn(Map.of("refresh_token", "rt-456"));

        Optional<String> result = appleTokenService.exchangeRefreshToken(
            "auth-code", "com.lut.web", "https://dev.level-up-together.com/oauth/callback/apple");

        assertThat(result).contains("rt-456");
    }

    @Test
    @DisplayName("응답에 refresh_token 이 없으면 empty 를 반환한다")
    void exchangeRefreshToken_noRefreshToken_returnsEmpty() {
        when(appleAuthFeignClient.exchangeToken(any(), any(), any(), any(), any()))
            .thenReturn(Map.of("access_token", "at"));

        assertThat(appleTokenService.exchangeRefreshToken("code", "cid", null)).isEmpty();
    }

    @Test
    @DisplayName("code 교환 실패는 empty 로 삼켜진다 — 로그인은 계속 진행돼야 한다")
    void exchangeRefreshToken_failure_returnsEmpty() {
        when(appleAuthFeignClient.exchangeToken(any(), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("invalid_grant"));

        assertThat(appleTokenService.exchangeRefreshToken("code", "cid", null)).isEmpty();
    }

    @Test
    @DisplayName("자격증명 미설정이면 code 교환을 스킵한다 (no-op)")
    void exchangeRefreshToken_notConfigured_skips() {
        oAuth2Properties.setApple(new OAuth2Properties.Apple());

        assertThat(appleTokenService.exchangeRefreshToken("code", "cid", null)).isEmpty();
        verifyNoInteractions(appleAuthFeignClient);
    }

    @Test
    @DisplayName("revoke 성공 시 true 를 반환한다")
    void revoke_success() {
        boolean result = appleTokenService.revoke("rt-123", "io.pinkspider.lut");

        assertThat(result).isTrue();
        verify(appleAuthFeignClient).revoke(
            eq("io.pinkspider.lut"), anyString(), eq("rt-123"), eq("refresh_token"));
    }

    @Test
    @DisplayName("revoke 실패는 false 로 삼켜진다 — 탈퇴는 계속 진행돼야 한다")
    void revoke_failure_returnsFalse() {
        doThrow(new RuntimeException("apple down"))
            .when(appleAuthFeignClient).revoke(any(), any(), any(), any());

        assertThat(appleTokenService.revoke("rt-123", "cid")).isFalse();
    }

    @Test
    @DisplayName("자격증명 미설정이면 revoke 를 스킵한다 (no-op)")
    void revoke_notConfigured_skips() {
        oAuth2Properties.setApple(new OAuth2Properties.Apple());

        assertThat(appleTokenService.revoke("rt-123", "cid")).isFalse();
        verifyNoInteractions(appleAuthFeignClient);
    }
}
