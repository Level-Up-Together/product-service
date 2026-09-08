package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.pinkspider.global.security.OAuth2Properties;
import io.pinkspider.leveluptogethermvp.userservice.core.feignclient.apple.AppleAuthFeignClient;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sign in with Apple 서버 토큰 처리 (LUT-477).
 *
 * <ul>
 *   <li>로그인 시 authorization code 교환 → refresh token 확보 (탈퇴 revoke 용)</li>
 *   <li>탈퇴 시 refresh token revoke — App Store 심사 5.1.1(v) 요건</li>
 * </ul>
 *
 * <p>client_id 는 code 를 발급받은 클라이언트와 일치해야 한다 — id_token 의 aud 에서 추출해
 * 전달받는다 (웹=서비스 ID, iOS 네이티브=번들 ID 가 자동으로 맞는 구조).
 * 자격증명(app.oauth2.apple.*) 미설정이면 전부 no-op — 로그인/탈퇴는 정상 진행된다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppleTokenService {

    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";
    private static final long CLIENT_SECRET_TTL_MS = 5 * 60 * 1000L; // 교환/revoke 직전 생성 — 5분이면 충분

    private final OAuth2Properties oAuth2Properties;
    private final AppleAuthFeignClient appleAuthFeignClient;

    public boolean isConfigured() {
        OAuth2Properties.Apple apple = oAuth2Properties.getApple();
        return apple != null
            && isNotBlank(apple.getTeamId())
            && isNotBlank(apple.getKeyId())
            && isNotBlank(apple.getPrivateKey());
    }

    /**
     * authorization code → refresh token 교환. 실패해도 로그인은 계속돼야 하므로 empty 를 반환한다.
     *
     * @param redirectUri 웹/Android 플로우의 code 는 발급 시 redirect_uri 와 동일 값 필요, iOS 네이티브는 null
     */
    public Optional<String> exchangeRefreshToken(String code, String clientId, String redirectUri) {
        if (!isConfigured()) {
            log.debug("Apple 자격증명 미설정 — code 교환 스킵");
            return Optional.empty();
        }
        try {
            Map<String, Object> response = appleAuthFeignClient.exchangeToken(
                clientId, generateClientSecret(clientId), "authorization_code", code, redirectUri);
            Object refreshToken = response.get("refresh_token");
            if (refreshToken == null) {
                log.warn("Apple code 교환 응답에 refresh_token 없음: clientId={}", clientId);
                return Optional.empty();
            }
            log.info("Apple refresh token 확보: clientId={}", clientId);
            return Optional.of(refreshToken.toString());
        } catch (Exception e) {
            log.warn("Apple code 교환 실패 (로그인은 계속 진행): clientId={}, error={}",
                clientId, e.getMessage());
            return Optional.empty();
        }
    }

    /** 탈퇴 시 refresh token revoke — best-effort */
    public boolean revoke(String refreshToken, String clientId) {
        if (!isConfigured()) {
            log.debug("Apple 자격증명 미설정 — revoke 스킵");
            return false;
        }
        try {
            appleAuthFeignClient.revoke(
                clientId, generateClientSecret(clientId), refreshToken, "refresh_token");
            log.info("Apple token revoke 완료: clientId={}", clientId);
            return true;
        } catch (Exception e) {
            log.warn("Apple token revoke 실패: clientId={}, error={}", clientId, e.getMessage());
            return false;
        }
    }

    /** SIWA 키(.p8, ES256)로 서명한 client_secret JWT — iss=팀ID, sub=clientId */
    String generateClientSecret(String clientId) throws Exception {
        OAuth2Properties.Apple apple = oAuth2Properties.getApple();
        ECPrivateKey privateKey = parsePrivateKey(apple.getPrivateKey());

        long now = System.currentTimeMillis();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(apple.getTeamId())
            .subject(clientId)
            .audience(APPLE_AUDIENCE)
            .issueTime(new Date(now))
            .expirationTime(new Date(now + CLIENT_SECRET_TTL_MS))
            .build();

        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(apple.getKeyId())
                .type(JOSEObjectType.JWT)
                .build(),
            claims);
        jwt.sign(new ECDSASigner(privateKey));
        return jwt.serialize();
    }

    private ECPrivateKey parsePrivateKey(String pem) throws Exception {
        String base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (ECPrivateKey) KeyFactory.getInstance("EC")
            .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
