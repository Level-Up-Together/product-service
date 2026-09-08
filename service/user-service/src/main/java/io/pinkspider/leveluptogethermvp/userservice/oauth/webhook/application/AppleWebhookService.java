package io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.security.OAuth2Properties;
import io.pinkspider.leveluptogethermvp.userservice.mypage.application.MyPageService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign in with Apple Server-to-Server Notification 처리 (LUT-476).
 *
 * <p>사용자가 Apple ID 설정에서 앱 연결을 끊거나(consent-revoked) Apple 계정을 삭제하면
 * (account-delete) Apple 이 ASC 에 등록된 엔드포인트로 JWS(payload)를 POST 한다.
 * Apple 공개키(JWKS)로 서명을 검증하고 users.provider_user_id(= apple sub)로 매핑해
 * 해당 계정을 탈퇴 처리한다.
 *
 * @see <a href="https://developer.apple.com/documentation/signinwithapple/processing-changes-for-sign-in-with-apple-accounts">Apple docs</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppleWebhookService {

    static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";

    private static final String EVENT_CONSENT_REVOKED = "consent-revoked";
    private static final String EVENT_ACCOUNT_DELETE = "account-delete";

    private final OAuth2Properties oAuth2Properties;
    private final UserRepository userRepository;
    private final MyPageService myPageService;
    private final ObjectMapper objectMapper;

    // JWKS 캐시 (kid -> RSAKey) — 미지의 kid 수신 시 재조회 (키 로테이션 대응)
    private final Map<String, RSAKey> jwksCache = new ConcurrentHashMap<>();

    @Transactional(transactionManager = "userTransactionManager")
    public void handleNotification(String signedPayload) {
        JWTClaimsSet claims = verifySignedPayload(signedPayload);
        processEvents(claims);
    }

    private JWTClaimsSet verifySignedPayload(String signedPayload) {
        try {
            SignedJWT jwt = SignedJWT.parse(signedPayload);
            String kid = jwt.getHeader().getKeyID();

            RSAKey key = resolveKey(kid);
            if (key == null || !jwt.verify(new RSASSAVerifier(key))) {
                throw new CustomException(
                    ApiStatus.INVALID_ACCESS.getResultCode(), "error.apple.webhook.invalid_signature");
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!APPLE_ISSUER.equals(claims.getIssuer())) {
                throw new CustomException(
                    ApiStatus.INVALID_ACCESS.getResultCode(), "error.apple.webhook.invalid_issuer");
            }
            Date exp = claims.getExpirationTime();
            if (exp != null && exp.before(new Date())) {
                throw new CustomException(
                    ApiStatus.INVALID_ACCESS.getResultCode(), "error.apple.webhook.expired");
            }
            validateAudience(claims.getAudience());
            return claims;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Apple 웹훅 페이로드 파싱/검증 실패: {}", e.getMessage());
            throw new CustomException(
                ApiStatus.INVALID_ACCESS.getResultCode(), "error.apple.webhook.invalid_payload");
        }
    }

    /** aud 허용 목록 검증 — 설정이 비어 있으면 생략 (kakaoWebhook.appId 관례) */
    private void validateAudience(List<String> audiences) {
        List<String> allowed = oAuth2Properties.getAppleWebhook().getAudiences();
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        if (audiences == null || audiences.stream().noneMatch(allowed::contains)) {
            log.warn("Apple 웹훅 audience 불일치 - aud: {}", audiences);
            throw new CustomException(
                ApiStatus.INVALID_ACCESS.getResultCode(), "error.apple.webhook.invalid_audience");
        }
    }

    private RSAKey resolveKey(String kid) throws Exception {
        RSAKey cached = jwksCache.get(kid);
        if (cached != null) {
            return cached;
        }
        JWKSet jwkSet = JWKSet.load(new URL(APPLE_JWKS_URL));
        jwkSet.getKeys().forEach(jwk -> {
            if (jwk instanceof RSAKey rsaKey && jwk.getKeyID() != null) {
                jwksCache.put(jwk.getKeyID(), rsaKey);
            }
        });
        return jwksCache.get(kid);
    }

    /**
     * events 클레임은 이벤트 하나를 담은 <b>JSON 문자열</b>이다.
     * {@code {"type":"consent-revoked","sub":"...","event_time":...}}
     */
    private void processEvents(JWTClaimsSet claims) {
        try {
            String eventsJson = claims.getStringClaim("events");
            if (eventsJson == null || eventsJson.isBlank()) {
                log.warn("Apple 웹훅에 events 클레임 없음");
                return;
            }
            JsonNode event = objectMapper.readTree(eventsJson);
            String type = event.path("type").asText(null);
            String sub = event.path("sub").asText(null);
            log.info("Apple 웹훅 이벤트 수신 - type: {}, sub: {}", type, sub);

            switch (type == null ? "" : type) {
                case EVENT_CONSENT_REVOKED, EVENT_ACCOUNT_DELETE ->
                    withdrawByAppleSub(sub, type);
                default -> log.info("별도 처리가 필요하지 않은 Apple 이벤트: {}", type);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple 웹훅 이벤트 처리 실패: {}", e.getMessage());
        }
    }

    /**
     * 연결 해제/계정 삭제 → 내부 계정 탈퇴 처리. 매핑이 없으면(provider_user_id 백필 전 유저
     * 또는 이미 탈퇴) 로그만 남긴다 — Apple 은 200 응답을 기대하므로 실패로 취급하지 않는다.
     */
    private void withdrawByAppleSub(String sub, String eventType) {
        if (sub == null || sub.isBlank()) {
            log.warn("Apple 웹훅 이벤트에 sub 없음 - type: {}", eventType);
            return;
        }
        Optional<Users> user = userRepository.findActiveByProviderAndProviderUserId("apple", sub);
        if (user.isEmpty()) {
            log.info("Apple 연결 해제 - 매핑되는 활성 사용자 없음 (백필 전 또는 기탈퇴): sub={}", sub);
            return;
        }
        String userId = user.get().getId();
        try {
            myPageService.withdrawUser(userId);
            log.info("Apple 연결 해제로 회원 탈퇴 처리 완료: userId={}, eventType={}", userId, eventType);
        } catch (Exception e) {
            log.error("Apple 연결 해제 탈퇴 처리 실패: userId={}, error={}", userId, e.getMessage());
        }
    }
}
