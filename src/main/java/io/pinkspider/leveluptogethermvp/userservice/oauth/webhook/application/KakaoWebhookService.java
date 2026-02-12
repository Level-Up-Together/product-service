package io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.security.OAuth2Properties;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.MultiDeviceTokenService;
import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.dto.KakaoSetPayload;
import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.dto.KakaoUnlinkWebhookRequest;
import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.enums.KakaoAccountEventType;
import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.enums.KakaoUnlinkReferrerType;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.UserStatus;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오 웹훅 처리 서비스
 *
 * 연결 해제 웹훅과 계정 상태 변경 웹훅을 처리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoWebhookService {

    private static final String KAKAO_ISSUER = "https://kauth.kakao.com";
    private static final String KAKAO_SSF_CONFIG_URL = "https://kauth.kakao.com/.well-known/ssf-configuration";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final OAuth2Properties oAuth2Properties;
    private final UserRepository userRepository;
    private final MultiDeviceTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(HTTP_TIMEOUT)
        .build();

    // JWKS 캐시 (kid -> RSAPublicKey)
    private final Map<String, RSAPublicKey> jwksCache = new ConcurrentHashMap<>();
    private volatile String cachedJwksUri = null;

    /**
     * 연결 해제 웹훅 처리
     *
     * @param authorization KakaoAK ${PRIMARY_ADMIN_KEY} 형식
     * @param request 웹훅 요청 정보
     */
    @Transactional(transactionManager = "userTransactionManager")
    public void handleUnlinkWebhook(String authorization, KakaoUnlinkWebhookRequest request) {
        // 1. 어드민 키 검증
        validateAdminKey(authorization);

        // 2. 앱 ID 검증
        validateAppId(request.getAppId());

        // 3. 연결 해제 처리
        processUnlink(request);
    }

    /**
     * 계정 상태 변경 웹훅 처리
     *
     * @param setToken SET(Security Event Token) JWT
     * @throws SetValidationException SET 검증 실패 시
     */
    @Transactional(transactionManager = "userTransactionManager")
    public void handleAccountStatusWebhook(String setToken) {
        // 1. SET 토큰 파싱 및 검증
        KakaoSetPayload payload = parseAndValidateSet(setToken);

        // 2. 이벤트 처리
        processAccountStatusEvent(payload);
    }

    private void validateAdminKey(String authorization) {
        if (authorization == null || !authorization.startsWith("KakaoAK ")) {
            log.warn("잘못된 Authorization 헤더 형식: {}", authorization);
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "Invalid authorization header format");
        }

        String providedKey = authorization.substring("KakaoAK ".length());
        String expectedKey = oAuth2Properties.getKakaoWebhook().getAdminKey();

        if (expectedKey == null || !expectedKey.equals(providedKey)) {
            log.warn("어드민 키 불일치");
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "Invalid admin key");
        }
    }

    private void validateAppId(String appId) {
        String expectedAppId = oAuth2Properties.getKakaoWebhook().getAppId();

        if (expectedAppId != null && !expectedAppId.equals(appId)) {
            log.warn("앱 ID 불일치 - expected: {}, actual: {}", expectedAppId, appId);
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "Invalid app id");
        }
    }

    private void processUnlink(KakaoUnlinkWebhookRequest request) {
        String kakaoUserId = request.getUserId();
        KakaoUnlinkReferrerType referrerType = KakaoUnlinkReferrerType.fromValue(request.getReferrerType());

        log.info("카카오 연결 해제 처리 시작 - kakaoUserId: {}, referrerType: {}", kakaoUserId, referrerType);

        // 카카오 provider를 사용하는 사용자 조회는 kakaoUserId로 직접 조회할 수 없음
        // 실제 구현에서는 별도의 매핑 테이블이 필요하거나, 사용자 정보에 kakaoUserId를 저장해야 함
        // 현재는 로그만 남기고, 실제 사용자 상태 변경은 별도 구현 필요
        log.info("카카오 연결 해제 처리 완료 - kakaoUserId: {}, referrerType: {}", kakaoUserId, referrerType);

        // TODO: 카카오 사용자 ID와 내부 사용자 ID 매핑 테이블 추가 후 구현
        // 1. 해당 사용자의 모든 토큰 무효화
        // 2. 사용자 상태 변경 (WITHDRAWN 또는 별도 상태)
    }

    private KakaoSetPayload parseAndValidateSet(String setToken) {
        try {
            // JWT를 헤더, 페이로드, 서명으로 분리
            String[] parts = setToken.split("\\.");
            if (parts.length != 3) {
                throw new SetValidationException("invalid_request", "Invalid JWT format");
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));

            JsonNode header = objectMapper.readTree(headerJson);
            JsonNode payload = objectMapper.readTree(payloadJson);

            // 헤더 검증
            String kid = header.get("kid").asText();
            String typ = header.get("typ").asText();
            String alg = header.get("alg").asText();

            if (!"secevent+jwt".equals(typ)) {
                throw new SetValidationException("invalid_request", "Invalid token type: " + typ);
            }

            if (!"RS256".equals(alg)) {
                throw new SetValidationException("invalid_request", "Unsupported algorithm: " + alg);
            }

            // Issuer 검증
            String issuer = payload.get("iss").asText();
            if (!KAKAO_ISSUER.equals(issuer)) {
                throw new SetValidationException("invalid_issuer", "Invalid issuer: " + issuer);
            }

            // Audience 검증 (REST API Key)
            String audience = payload.get("aud").asText();
            String expectedAudience = oAuth2Properties.getKakaoWebhook().getRestApiKey();
            if (expectedAudience != null && !expectedAudience.equals(audience)) {
                throw new SetValidationException("invalid_audience", "Invalid audience: " + audience);
            }

            // 서명 검증
            verifySignature(setToken, kid, parts);

            // 페이로드 파싱
            return parseSetPayload(payload);

        } catch (SetValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("SET 토큰 파싱 실패", e);
            throw new SetValidationException("invalid_request", "Failed to parse SET token: " + e.getMessage());
        }
    }

    private void verifySignature(String setToken, String kid, String[] parts) {
        try {
            RSAPublicKey publicKey = getPublicKey(kid);
            if (publicKey == null) {
                throw new SetValidationException("invalid_key", "Public key not found for kid: " + kid);
            }

            // 서명 검증
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update((parts[0] + "." + parts[1]).getBytes());

            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
            if (!sig.verify(signatureBytes)) {
                throw new SetValidationException("invalid_key", "Signature verification failed");
            }
        } catch (SetValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("서명 검증 실패", e);
            throw new SetValidationException("invalid_key", "Signature verification failed: " + e.getMessage());
        }
    }

    private RSAPublicKey getPublicKey(String kid) {
        // 캐시에서 먼저 조회
        if (jwksCache.containsKey(kid)) {
            return jwksCache.get(kid);
        }

        // JWKS URI 조회
        String jwksUri = getJwksUri();
        if (jwksUri == null) {
            return null;
        }

        // JWKS에서 공개키 조회
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jwksUri))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode jwks = objectMapper.readTree(response.body());
            JsonNode keys = jwks.get("keys");

            for (JsonNode key : keys) {
                String keyId = key.get("kid").asText();
                if (kid.equals(keyId)) {
                    RSAPublicKey publicKey = parseRsaPublicKey(key);
                    jwksCache.put(kid, publicKey);
                    return publicKey;
                }
            }
        } catch (Exception e) {
            log.error("JWKS 조회 실패", e);
        }

        return null;
    }

    private String getJwksUri() {
        if (cachedJwksUri != null) {
            return cachedJwksUri;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KAKAO_SSF_CONFIG_URL))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode config = objectMapper.readTree(response.body());
            cachedJwksUri = config.get("jwks_uri").asText();
            return cachedJwksUri;
        } catch (Exception e) {
            log.error("SSF Configuration 조회 실패", e);
            return null;
        }
    }

    private RSAPublicKey parseRsaPublicKey(JsonNode keyNode) throws Exception {
        String n = keyNode.get("n").asText();
        String e = keyNode.get("e").asText();

        byte[] nBytes = Base64.getUrlDecoder().decode(n);
        byte[] eBytes = Base64.getUrlDecoder().decode(e);

        BigInteger modulus = new BigInteger(1, nBytes);
        BigInteger exponent = new BigInteger(1, eBytes);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) factory.generatePublic(spec);
    }

    private KakaoSetPayload parseSetPayload(JsonNode payload) {
        String sub = payload.get("sub").asText();
        long iat = payload.get("iat").asLong();
        long toe = payload.has("toe") ? payload.get("toe").asLong() : iat;
        String jti = payload.get("jti").asText();

        JsonNode events = payload.get("events");

        KakaoSetPayload.KakaoSetPayloadBuilder builder = KakaoSetPayload.builder()
            .sub(sub)
            .iat(iat)
            .toe(toe)
            .jti(jti);

        // 이벤트 타입 파싱
        events.fieldNames().forEachRemaining(eventUri -> {
            KakaoAccountEventType eventType = KakaoAccountEventType.fromUri(eventUri);
            if (eventType != null) {
                builder.eventType(eventType);
                JsonNode eventData = events.get(eventUri);
                if (eventData != null && !eventData.isEmpty()) {
                    try {
                        Map<String, Object> data = objectMapper.convertValue(
                            eventData, new TypeReference<Map<String, Object>>() {});
                        builder.eventData(data);
                    } catch (Exception e) {
                        log.warn("이벤트 데이터 파싱 실패: {}", eventUri, e);
                    }
                }
            } else {
                log.warn("알 수 없는 이벤트 타입: {}", eventUri);
            }
        });

        return builder.build();
    }

    private void processAccountStatusEvent(KakaoSetPayload payload) {
        KakaoAccountEventType eventType = payload.getEventType();
        String kakaoUserId = payload.getSub();

        log.info("카카오 계정 상태 이벤트 처리 - eventType: {}, kakaoUserId: {}", eventType, kakaoUserId);

        if (eventType == null) {
            log.warn("처리할 수 없는 이벤트 타입");
            return;
        }

        switch (eventType) {
            case USER_UNLINKED -> handleUserUnlinked(kakaoUserId, payload);
            case ACCOUNT_DISABLED -> handleAccountDisabled(kakaoUserId, payload);
            case ACCOUNT_ENABLED -> handleAccountEnabled(kakaoUserId, payload);
            case TOKENS_REVOKED, SESSIONS_REVOKED -> handleTokensRevoked(kakaoUserId);
            case CREDENTIAL_CHANGE, CREDENTIAL_COMPROMISE -> handleCredentialChange(kakaoUserId);
            default -> log.info("별도 처리가 필요하지 않은 이벤트: {}", eventType);
        }
    }

    private void handleUserUnlinked(String kakaoUserId, KakaoSetPayload payload) {
        log.info("사용자 앱 연결 해제 처리 - kakaoUserId: {}", kakaoUserId);

        // TODO: 카카오 사용자 ID와 내부 사용자 ID 매핑 후 구현
        // 1. 해당 사용자의 모든 토큰 무효화
        // 2. 필요시 사용자 상태 변경
    }

    private void handleAccountDisabled(String kakaoUserId, KakaoSetPayload payload) {
        log.info("카카오 계정 비활성화 처리 - kakaoUserId: {}", kakaoUserId);

        // 카카오 계정이 비활성화되면 해당 사용자의 세션도 무효화
        // TODO: 카카오 사용자 ID 매핑 후 구현
    }

    private void handleAccountEnabled(String kakaoUserId, KakaoSetPayload payload) {
        log.info("카카오 계정 활성화 처리 - kakaoUserId: {}", kakaoUserId);
        // 계정 활성화는 별도 처리 불필요 (다음 로그인 시 정상 처리)
    }

    private void handleTokensRevoked(String kakaoUserId) {
        log.info("카카오 토큰 만료 처리 - kakaoUserId: {}", kakaoUserId);

        // TODO: 카카오 사용자 ID 매핑 후 구현
        // 해당 사용자의 모든 세션 토큰 무효화
    }

    private void handleCredentialChange(String kakaoUserId) {
        log.info("카카오 자격증명 변경 처리 - kakaoUserId: {}", kakaoUserId);

        // 보안 강화를 위해 모든 세션 무효화 권장
        // TODO: 카카오 사용자 ID 매핑 후 구현
    }

    /**
     * SET 검증 실패 예외
     */
    @Getter
    public static class SetValidationException extends RuntimeException {
        private final String errorCode;
        private final String errorDescription;

        public SetValidationException(String errorCode, String errorDescription) {
            super(errorCode + ": " + errorDescription);
            this.errorCode = errorCode;
            this.errorDescription = errorDescription;
        }
    }
}
