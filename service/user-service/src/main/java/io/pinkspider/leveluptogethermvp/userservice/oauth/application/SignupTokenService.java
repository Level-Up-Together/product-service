package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.SignupSessionData;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 신규 사용자 회원가입을 위한 임시 signup token 관리 (QA-108)
 *
 * <p>OAuth 콜백 시 신규 사용자를 곧바로 DB에 INSERT하지 않고, signup token으로 임시 세션을 만들어
 * 닉네임 설정 + 약관 동의가 모두 완료되었을 때 비로소 INSERT한다.
 *
 * <p>Redis 키 구조:
 * <ul>
 *   <li>{@code signup:{provider}:{emailHash}} → JSON 직렬화된 SignupSessionData</li>
 *   <li>{@code signup-token:{signupToken}} → "{provider}:{emailHash}" 인덱스</li>
 * </ul>
 * 양방향 인덱싱으로 토큰/이메일 양쪽에서 조회 가능.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SignupTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final String SESSION_KEY_PREFIX = "signup:";
    private static final String TOKEN_INDEX_PREFIX = "signup-token:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 신규 signup token 발급. 같은 (provider, email)로 진행 중인 세션이 있으면 이전 token을 무효화하고 새 token으로 교체한다.
     */
    public String createOrRefresh(SignupSessionData data) {
        String emailHash = hashEmail(data.provider(), data.email());
        String sessionKey = sessionKey(data.provider(), emailHash);

        // 이전 세션이 있으면 토큰 인덱스만 무효화 (세션 데이터는 덮어씀)
        String existingJson = redisTemplate.opsForValue().get(sessionKey);
        if (existingJson != null) {
            try {
                SignupSessionData existing = objectMapper.readValue(existingJson, SignupSessionData.class);
                redisTemplate.delete(tokenIndexKey(existing.signupToken()));
            } catch (JsonProcessingException e) {
                log.warn("기존 signup session 파싱 실패: {}", e.getMessage());
            }
        }

        String newToken = UUID.randomUUID().toString();
        SignupSessionData refreshed = data.withToken(newToken);

        try {
            String json = objectMapper.writeValueAsString(refreshed);
            redisTemplate.opsForValue().set(sessionKey, json, TOKEN_TTL);
            redisTemplate.opsForValue().set(tokenIndexKey(newToken),
                refreshed.provider() + ":" + emailHash, TOKEN_TTL);
        } catch (JsonProcessingException e) {
            throw new CustomException(ApiStatus.SYSTEM_ERROR.getResultCode(),
                "signup session 직렬화 실패: " + e.getMessage());
        }

        log.info("Signup token 발급: provider={}, token={}, ttlMin={}",
            data.provider(), maskToken(newToken), TOKEN_TTL.toMinutes());
        return newToken;
    }

    /**
     * signup token으로 세션 데이터 조회 (만료/미존재 시 예외).
     */
    public SignupSessionData findByToken(String signupToken) {
        if (signupToken == null || signupToken.isBlank()) {
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "error.signup.token_invalid");
        }
        String sessionKeyValue = redisTemplate.opsForValue().get(tokenIndexKey(signupToken));
        if (sessionKeyValue == null) {
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "error.signup.token_expired");
        }
        String json = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + sessionKeyValue);
        if (json == null) {
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "error.signup.token_expired");
        }
        try {
            return objectMapper.readValue(json, SignupSessionData.class);
        } catch (JsonProcessingException e) {
            throw new CustomException(ApiStatus.SYSTEM_ERROR.getResultCode(),
                "signup session 파싱 실패: " + e.getMessage());
        }
    }

    /**
     * 토큰 유효성만 확인 (예외 미발생).
     */
    public boolean isValid(String signupToken) {
        if (signupToken == null || signupToken.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(tokenIndexKey(signupToken)));
    }

    /**
     * 가입 완료 후 세션 데이터 + 토큰 인덱스 삭제.
     */
    public void delete(SignupSessionData data) {
        String emailHash = hashEmail(data.provider(), data.email());
        redisTemplate.delete(sessionKey(data.provider(), emailHash));
        redisTemplate.delete(tokenIndexKey(data.signupToken()));
        log.info("Signup token 삭제: provider={}, token={}", data.provider(), maskToken(data.signupToken()));
    }

    public static String hashEmail(String provider, String email) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((provider + ":" + email).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private static String sessionKey(String provider, String emailHash) {
        return SESSION_KEY_PREFIX + provider + ":" + emailHash;
    }

    private static String tokenIndexKey(String token) {
        return TOKEN_INDEX_PREFIX + token;
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 8) + "***";
    }
}
