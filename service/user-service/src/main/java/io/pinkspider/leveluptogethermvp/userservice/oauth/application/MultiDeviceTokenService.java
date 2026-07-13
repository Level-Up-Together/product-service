package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.global.security.TokenBlacklistChecker;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response.SessionsResponseDto.Session;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MultiDeviceTokenService implements TokenBlacklistChecker {

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final SlidingExpirationService slidingExpirationService;
    private final ObjectMapper objectMapper;

    // rotation 직후 응답 유실로 구 refresh 토큰이 재시도되는 것을 허용하는 grace window.
    // 이 창 안에서 previousRefreshToken 으로 재시도하면 새 토큰을 재발급한다 (grace retry).
    private static final long ROTATION_GRACE_MILLIS = Duration.ofMinutes(2).toMillis();

    // 세션 TTL 버퍼 — refresh 만료 직후 grace/시계 오차 케이스에서 세션이 먼저 사라지지 않게 한다
    private static final Duration SESSION_TTL_BUFFER = Duration.ofDays(1);

    // QA-231: refresh 토큰은 평문 대신 해시로 저장한다. prefix 로 레거시 평문과 구분.
    private static final String HASH_PREFIX = "sha256:";

    /** 저장된 refresh 토큰과 제시된 토큰의 비교 결과 */
    public enum RefreshTokenMatch {
        MATCH,
        MISMATCH,
        NO_SESSION
    }

    public void saveTokensToRedis(String userId, String deviceType,
                                  String deviceId, String accessToken, String refreshToken) {

        String sessionKey = buildSessionKey(userId, deviceType, deviceId);

        // 세션 데이터 저장 — 토큰 원문 대신 해시 + 블랙리스트/만료 판정용 메타데이터(jti, exp)
        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("refreshToken", hashToken(refreshToken));
        sessionData.put("loginTime", String.valueOf(System.currentTimeMillis()));
        sessionData.put("deviceType", deviceType);
        sessionData.put("deviceId", deviceId);
        sessionData.put("userId", userId);
        putTokenMetadata(sessionData, "refresh", refreshToken);
        putTokenMetadata(sessionData, "access", accessToken);

        Duration sessionTtl = sessionTtl(refreshToken);
        redisTemplate.opsForHash().putAll(sessionKey, sessionData);
        redisTemplate.expire(sessionKey, sessionTtl);

        // 사용자별 활성 세션 목록 관리
        String userSessionsKey = "userSessions:" + userId;
        redisTemplate.opsForSet().add(userSessionsKey, sessionKey);
        redisTemplate.expire(userSessionsKey, sessionTtl);
    }

    // 기존 토큰들을 업데이트 (Access Token은 항상, Refresh Token은 선택적)
    public void updateTokens(String userId, String deviceType, String deviceId,
                             String newAccessToken, String newRefreshToken) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);

        updateAccessTokenMetadata(sessionKey, newAccessToken);

        if (newRefreshToken != null) {
            // rotation: 현재 토큰(해시)은 grace 재시도용 previous 로 보관하고,
            // 한 세대 전 previous 는 이 시점에 블랙리스트 처리한다 (동시 유효 토큰을 2개로 제한)
            blacklistStoredPrevious(sessionKey);
            shiftCurrentToPrevious(sessionKey);
            putCurrentRefresh(sessionKey, newRefreshToken);
            redisTemplate.opsForHash().put(sessionKey, "lastRefreshTime",
                String.valueOf(System.currentTimeMillis()));
            log.info("Refresh token renewed for user: {}, device: {}", userId, deviceId);
        }

        // 세션 TTL 을 refresh 토큰 잔여 유효기간에 정렬 (토큰은 유효한데 세션만 소멸하는 상태 방지)
        Duration sessionTtl = newRefreshToken != null
            ? sessionTtl(newRefreshToken)
            : sessionTtlFromStored(sessionKey);
        redisTemplate.expire(sessionKey, sessionTtl);
        redisTemplate.expire("userSessions:" + userId, sessionTtl);
    }

    /**
     * grace 재시도용 갱신: 새 access/refresh 를 현재 토큰으로 교체하되
     * previous(직전 rotation 의 구 토큰) 기록은 유지한다.
     * 응답이 반복 유실되어도 grace window 내에서는 같은 구 토큰으로 계속 재시도할 수 있다.
     */
    public void updateTokensForGraceRetry(String userId, String deviceType, String deviceId,
                                          String newAccessToken, String newRefreshToken) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);

        updateAccessTokenMetadata(sessionKey, newAccessToken);
        putCurrentRefresh(sessionKey, newRefreshToken);
        redisTemplate.opsForHash().put(sessionKey, "lastRefreshTime",
            String.valueOf(System.currentTimeMillis()));

        Duration sessionTtl = sessionTtl(newRefreshToken);
        redisTemplate.expire(sessionKey, sessionTtl);
        redisTemplate.expire("userSessions:" + userId, sessionTtl);
    }

    /** 제시된 refresh 토큰이 세션의 현재 토큰과 일치하는지 (해시/레거시 평문 모두 지원) */
    public RefreshTokenMatch checkRefreshToken(String userId, String deviceType, String deviceId,
                                               String presentedToken) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);
        String stored = (String) redisTemplate.opsForHash().get(sessionKey, "refreshToken");
        if (stored == null) {
            return RefreshTokenMatch.NO_SESSION;
        }
        return matchesStored(stored, presentedToken)
            ? RefreshTokenMatch.MATCH
            : RefreshTokenMatch.MISMATCH;
    }

    /**
     * rotation 직후 응답 유실로 클라이언트가 구 refresh 토큰을 다시 보낸 재시도인지 확인한다.
     * previousRefreshToken 과 일치하고 rotation 후 grace window 이내일 때만 true.
     */
    public boolean isWithinRotationGrace(String userId, String deviceType, String deviceId,
                                         String presentedToken) {
        if (presentedToken == null) {
            return false;
        }
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);
        String previous = (String) redisTemplate.opsForHash().get(sessionKey, "previousRefreshToken");
        String previousTimeRaw =
            (String) redisTemplate.opsForHash().get(sessionKey, "previousRefreshTime");
        if (previous == null || previousTimeRaw == null || !matchesStored(previous, presentedToken)) {
            return false;
        }
        try {
            long previousTime = Long.parseLong(previousTimeRaw);
            return System.currentTimeMillis() - previousTime <= ROTATION_GRACE_MILLIS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 세션 최초 로그인 시각 (절대 상한 판정용). 세션이 없거나 값이 손상됐으면 null. */
    public Long getLoginTime(String userId, String deviceType, String deviceId) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);
        Object loginTime = redisTemplate.opsForHash().get(sessionKey, "loginTime");
        if (loginTime == null) {
            return null;
        }
        try {
            return Long.parseLong(loginTime.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Refresh Token 갱신이 필요한지 확인 (SlidingExpirationService 사용)
    public boolean shouldRenewRefreshToken(String refreshToken) {
        return slidingExpirationService.shouldRenewRefreshToken(refreshToken);
    }

    // 토큰 갱신 가능 여부 확인
    public boolean canRenewRefreshToken(String refreshToken) {
        return slidingExpirationService.canRenewToken(refreshToken);
    }

    // 토큰이 블랙리스트에 있는지 확인
    public boolean isTokenBlacklisted(String token) {
        if (token == null) {
            return true;
        }

        try {
            String jti = jwtUtil.getJtiFromToken(token);
            return redisTemplate.hasKey("blacklist:" + jti);
        } catch (Exception e) {
            log.warn("Failed to check token blacklist: {}", e.getMessage());
            return true;
        }
    }

    // 토큰(원문)을 블랙리스트에 추가
    public void blacklistToken(String token) {
        if (token == null) {
            return;
        }

        try {
            if (jwtUtil.validateToken(token)) {
                String jti = jwtUtil.getJtiFromToken(token);
                long remainingTime = jwtUtil.getRemainingTime(token);
                blacklistJti(jti, System.currentTimeMillis() + remainingTime);
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist token: {}", e.getMessage());
        }
    }

    // jti 기반 블랙리스트 — 해시 저장으로 토큰 원문이 없는 경우 사용
    private void blacklistJti(String jti, long expiresAtMillis) {
        if (jti == null) {
            return;
        }
        long remaining = expiresAtMillis - System.currentTimeMillis();
        if (remaining > 0) {
            redisTemplate.opsForValue().set("blacklist:" + jti, "revoked",
                Duration.ofMillis(remaining));
            log.debug("Token blacklisted: {}", jti);
        }
    }

    // 특정 디바이스 로그아웃
    public void logout(String userId, String deviceType, String deviceId) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);

        try {
            // 토큰들을 블랙리스트에 추가 (해시 세션은 jti 메타데이터로, 레거시는 원문으로)
            blacklistFromSession(sessionKey, "access", "accessToken");
            blacklistFromSession(sessionKey, "refresh", "refreshToken");
            blacklistStoredPrevious(sessionKey);

            // 세션 삭제
            redisTemplate.delete(sessionKey);
            redisTemplate.opsForSet().remove("userSessions:" + userId, sessionKey);

            log.info("User logged out: {}, device: {}", userId, deviceId);

        } catch (Exception e) {
            log.error("Failed to logout user: {}, device: {}", userId, deviceId, e);
        }
    }

    // 모든 디바이스에서 로그아웃
    public void logoutAllDevices(String userId) {
        String userSessionsKey = "userSessions:" + userId;

        try {
            Set<String> sessions = redisTemplate.opsForSet().members(userSessionsKey);

            if (sessions != null && !sessions.isEmpty()) {
                for (String sessionKey : sessions) {
                    blacklistFromSession(sessionKey, "access", "accessToken");
                    blacklistFromSession(sessionKey, "refresh", "refreshToken");
                    blacklistStoredPrevious(sessionKey);

                    redisTemplate.delete(sessionKey);
                }

                log.info("All devices logged out for user: {}, sessions: {}", userId, sessions.size());
            }

            redisTemplate.delete(userSessionsKey);

        } catch (Exception e) {
            log.error("Failed to logout all devices for user: {}", userId, e);
        }
    }

    /**
     * 특정 세션의 상태 정보 조회 (token-status API 용).
     * QA-231: 토큰 원문은 포함하지 않는다 — 만료/갱신 판정값만 반환.
     */
    public Map<String, Object> getSessionInfo(String userId, String deviceType, String deviceId) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);
        Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(sessionKey);

        Map<String, Object> result = new HashMap<>();
        // 토큰 값 필드는 응답에서 제외
        sessionData.forEach((k, v) -> {
            String key = k.toString();
            if (!key.equals("refreshToken") && !key.equals("previousRefreshToken")
                && !key.equals("accessToken")) {
                result.put(key, v);
            }
        });

        Long refreshRemaining = refreshRemainingMillis(sessionData);
        if (refreshRemaining != null) {
            Long loginTime = parseLongOrNull(sessionData.get("loginTime"));
            boolean valid = refreshRemaining > 0;
            boolean shouldRenew =
                slidingExpirationService.shouldRenewByRemainingMillis(refreshRemaining);
            result.put("refreshTokenRemaining", refreshRemaining);
            result.put("refreshTokenValid", valid);
            result.put("shouldRenewRefreshToken", shouldRenew);
            result.put("canRenewRefreshToken",
                valid && shouldRenew && slidingExpirationService.isSessionWithinMaxLifetime(loginTime));
        }

        return result;
    }

    // 사용자의 모든 활성 세션 조회 — QA-231: 토큰 원문은 응답에 포함하지 않는다
    public List<Session> getActiveSessions(String userId) {
        String userSessionsKey = "userSessions:" + userId;
        Set<String> sessions = redisTemplate.opsForSet().members(userSessionsKey);

        List<Session> activeSessions = new ArrayList<>();

        if (sessions != null) {
            for (String sessionKey : sessions) {
                Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(sessionKey);

                if (!sessionData.isEmpty()) {
                    Session.SessionBuilder builder = Session.builder()
                        .deviceType(asString(sessionData.get("deviceType")))
                        .deviceId(asString(sessionData.get("deviceId")))
                        .loginTime(asString(sessionData.get("loginTime")))
                        .memberId(asString(sessionData.get("userId")));

                    Long refreshRemaining = refreshRemainingMillis(sessionData);
                    if (refreshRemaining != null) {
                        builder.refreshTokenRemaining(
                            java.math.BigInteger.valueOf(Math.max(0, refreshRemaining)));
                        builder.refreshTokenValid(refreshRemaining > 0);
                        builder.shouldRenew(
                            slidingExpirationService.shouldRenewByRemainingMillis(refreshRemaining));
                    }

                    Long accessRemaining = accessRemainingMillis(sessionData);
                    if (accessRemaining != null) {
                        builder.accessTokenRemaining(
                            java.math.BigInteger.valueOf(Math.max(0, accessRemaining)));
                        builder.accessTokenValid(accessRemaining > 0);
                    }

                    activeSessions.add(builder.build());
                }
            }
        }

        return activeSessions;
    }

    // TODO 세션이 존재하는지 확인
    public boolean sessionExists(String userId, String deviceType, String deviceId) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);
        return redisTemplate.hasKey(sessionKey);
    }

    // 세션 통계 조회
    public Session getSessionStats(String userId) {
        List<Session> sessions = getActiveSessions(userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSessions", sessions.size());

        Map<String, Long> deviceTypeCount = sessions.stream()
            .collect(Collectors.groupingBy(
                session -> session.getDeviceType(),
                Collectors.counting()
            ));
        stats.put("deviceTypeCounts", deviceTypeCount);

        if (!sessions.isEmpty()) {
            OptionalLong oldestLogin = sessions.stream()
                .mapToLong(session -> Long.parseLong(session.getLoginTime()))
                .min();

            OptionalLong newestLogin = sessions.stream()
                .mapToLong(session -> Long.parseLong(session.getLoginTime()))
                .max();

            if (oldestLogin.isPresent()) {
                stats.put("oldestLoginTime", oldestLogin.getAsLong());
            }
            if (newestLogin.isPresent()) {
                stats.put("newestLoginTime", newestLogin.getAsLong());
            }
        }

        return objectMapper.convertValue(stats, Session.class);
    }

    // 만료된 세션 정리 (스케줄러에서 사용)
    public int cleanupExpiredSessions() {
        Set<String> sessionKeys = redisTemplate.keys("session:*");
        int cleanedCount = 0;

        if (sessionKeys != null) {
            for (String sessionKey : sessionKeys) {
                if (isSessionRefreshExpired(sessionKey)) {
                    // 세션에서 사용자 ID 추출하여 user_sessions에서도 제거
                    String[] parts = sessionKey.split(":");
                    if (parts.length >= 2) {
                        String userId = parts[1];
                        redisTemplate.opsForSet().remove("userSessions:" + userId, sessionKey);
                    }

                    redisTemplate.delete(sessionKey);
                    cleanedCount++;
                }
            }
        }

        log.info("Cleaned up {} expired sessions", cleanedCount);
        return cleanedCount;
    }

    /** 세션의 refresh 가 만료됐는지 — 해시 세션은 exp 메타데이터, 레거시는 원문 검증 */
    private boolean isSessionRefreshExpired(String sessionKey) {
        String refreshExpiresAt =
            (String) redisTemplate.opsForHash().get(sessionKey, "refreshExpiresAt");
        Long expiresAt = parseLongOrNull(refreshExpiresAt);
        if (expiresAt != null) {
            return expiresAt <= System.currentTimeMillis();
        }

        String stored = (String) redisTemplate.opsForHash().get(sessionKey, "refreshToken");
        if (stored == null) {
            return true;
        }
        if (stored.startsWith(HASH_PREFIX)) {
            // 해시인데 exp 메타데이터가 없는 비정상 세션 — 정리 대상으로 보지 않고 TTL 에 맡긴다
            return false;
        }
        return !jwtUtil.validateToken(stored);
    }

    // ===== QA-231 해시 저장 내부 헬퍼 =====

    /** SHA-256 해시 (prefix 포함). 저장 전용 — 비교는 matchesStored 사용. */
    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HASH_PREFIX + HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // JVM 표준 알고리즘 — 발생할 수 없음
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** 저장값(해시 또는 레거시 평문)과 제시된 토큰 원문 비교 */
    private boolean matchesStored(String stored, String presented) {
        if (stored == null || presented == null) {
            return false;
        }
        if (stored.startsWith(HASH_PREFIX)) {
            return MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                hashToken(presented).getBytes(StandardCharsets.UTF_8));
        }
        // 레거시 평문 세션 하위 호환
        return MessageDigest.isEqual(
            stored.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    /** 토큰의 jti/exp 메타데이터를 세션 맵에 기록 ({prefix}Jti / {prefix}ExpiresAt) */
    private void putTokenMetadata(Map<String, String> target, String prefix, String rawToken) {
        try {
            target.put(prefix + "Jti", jwtUtil.getJtiFromToken(rawToken));
            target.put(prefix + "ExpiresAt",
                String.valueOf(System.currentTimeMillis() + jwtUtil.getRemainingTime(rawToken)));
        } catch (Exception e) {
            log.warn("Failed to extract token metadata ({}): {}", prefix, e.getMessage());
        }
    }

    /** access 토큰 메타데이터 갱신 — 원문(accessToken 필드)은 저장하지 않고 레거시 필드는 제거 */
    private void updateAccessTokenMetadata(String sessionKey, String newAccessToken) {
        Map<String, String> metadata = new HashMap<>();
        putTokenMetadata(metadata, "access", newAccessToken);
        if (!metadata.isEmpty()) {
            redisTemplate.opsForHash().putAll(sessionKey, metadata);
        }
        redisTemplate.opsForHash().delete(sessionKey, "accessToken");
    }

    /** 현재 refresh(해시+메타데이터) 기록 */
    private void putCurrentRefresh(String sessionKey, String rawRefreshToken) {
        Map<String, String> fields = new HashMap<>();
        fields.put("refreshToken", hashToken(rawRefreshToken));
        putTokenMetadata(fields, "refresh", rawRefreshToken);
        redisTemplate.opsForHash().putAll(sessionKey, fields);
    }

    /** 현재 refresh 기록(해시/메타데이터)을 previous 로 이동 (grace 재시도용) */
    private void shiftCurrentToPrevious(String sessionKey) {
        String currentValue = (String) redisTemplate.opsForHash().get(sessionKey, "refreshToken");
        if (currentValue == null) {
            return;
        }
        Map<String, String> fields = new HashMap<>();
        fields.put("previousRefreshToken", currentValue);
        fields.put("previousRefreshTime", String.valueOf(System.currentTimeMillis()));

        String currentJti = (String) redisTemplate.opsForHash().get(sessionKey, "refreshJti");
        String currentExp = (String) redisTemplate.opsForHash().get(sessionKey, "refreshExpiresAt");
        if (currentJti == null && !currentValue.startsWith(HASH_PREFIX)) {
            // 레거시 평문 세션 — 원문에서 메타데이터 추출
            try {
                currentJti = jwtUtil.getJtiFromToken(currentValue);
                currentExp = String.valueOf(
                    System.currentTimeMillis() + jwtUtil.getRemainingTime(currentValue));
            } catch (Exception e) {
                log.warn("Failed to extract legacy refresh metadata: {}", e.getMessage());
            }
        }
        if (currentJti != null) {
            fields.put("previousRefreshJti", currentJti);
        }
        if (currentExp != null) {
            fields.put("previousRefreshExpiresAt", currentExp);
        }
        redisTemplate.opsForHash().putAll(sessionKey, fields);
    }

    /** previous refresh 토큰 블랙리스트 — jti 메타데이터 우선, 레거시 평문이면 원문으로 */
    private void blacklistStoredPrevious(String sessionKey) {
        String previousJti =
            (String) redisTemplate.opsForHash().get(sessionKey, "previousRefreshJti");
        Long previousExp = parseLongOrNull(
            redisTemplate.opsForHash().get(sessionKey, "previousRefreshExpiresAt"));
        if (previousJti != null && previousExp != null) {
            blacklistJti(previousJti, previousExp);
            return;
        }
        String previousRaw =
            (String) redisTemplate.opsForHash().get(sessionKey, "previousRefreshToken");
        if (previousRaw != null && !previousRaw.startsWith(HASH_PREFIX)) {
            blacklistToken(previousRaw);
        }
    }

    /** 세션 필드({prefix}Jti/{prefix}ExpiresAt 또는 레거시 원문)를 이용해 블랙리스트 */
    private void blacklistFromSession(String sessionKey, String metadataPrefix, String legacyField) {
        String jti = (String) redisTemplate.opsForHash().get(sessionKey, metadataPrefix + "Jti");
        Long expiresAt = parseLongOrNull(
            redisTemplate.opsForHash().get(sessionKey, metadataPrefix + "ExpiresAt"));
        if (jti != null && expiresAt != null) {
            blacklistJti(jti, expiresAt);
            return;
        }
        String legacyRaw = (String) redisTemplate.opsForHash().get(sessionKey, legacyField);
        if (legacyRaw != null && !legacyRaw.startsWith(HASH_PREFIX)) {
            blacklistToken(legacyRaw);
        }
    }

    /** refresh 잔여시간(ms) — exp 메타데이터 우선, 레거시 평문이면 원문에서 계산. 불명이면 null */
    private Long refreshRemainingMillis(Map<Object, Object> sessionData) {
        Long expiresAt = parseLongOrNull(sessionData.get("refreshExpiresAt"));
        if (expiresAt != null) {
            return expiresAt - System.currentTimeMillis();
        }
        Object stored = sessionData.get("refreshToken");
        if (stored != null && !stored.toString().startsWith(HASH_PREFIX)) {
            try {
                return jwtUtil.getRemainingTime(stored.toString());
            } catch (Exception e) {
                return 0L;
            }
        }
        return null;
    }

    /** access 잔여시간(ms) — exp 메타데이터 우선, 레거시 평문 fallback */
    private Long accessRemainingMillis(Map<Object, Object> sessionData) {
        Long expiresAt = parseLongOrNull(sessionData.get("accessExpiresAt"));
        if (expiresAt != null) {
            return expiresAt - System.currentTimeMillis();
        }
        Object legacyRaw = sessionData.get("accessToken");
        if (legacyRaw != null) {
            try {
                return jwtUtil.getRemainingTime(legacyRaw.toString());
            } catch (Exception e) {
                return 0L;
            }
        }
        return null;
    }

    // refresh 토큰 잔여 유효기간 + 버퍼. 잔여시간을 읽지 못하면 버퍼만 적용(곧 정리 대상).
    private Duration sessionTtl(String refreshToken) {
        long remaining = 0L;
        try {
            remaining = jwtUtil.getRemainingTime(refreshToken);
        } catch (Exception e) {
            log.warn("Failed to read refresh token remaining time: {}", e.getMessage());
        }
        if (remaining <= 0) {
            return SESSION_TTL_BUFFER;
        }
        return Duration.ofMillis(remaining).plus(SESSION_TTL_BUFFER);
    }

    /** 저장된 세션의 refresh exp 메타데이터(또는 레거시 원문)로 TTL 계산 */
    private Duration sessionTtlFromStored(String sessionKey) {
        Long expiresAt = parseLongOrNull(
            redisTemplate.opsForHash().get(sessionKey, "refreshExpiresAt"));
        if (expiresAt != null) {
            long remaining = expiresAt - System.currentTimeMillis();
            return remaining > 0
                ? Duration.ofMillis(remaining).plus(SESSION_TTL_BUFFER)
                : SESSION_TTL_BUFFER;
        }
        String stored = (String) redisTemplate.opsForHash().get(sessionKey, "refreshToken");
        if (stored != null && !stored.startsWith(HASH_PREFIX)) {
            return sessionTtl(stored);
        }
        return SESSION_TTL_BUFFER;
    }

    private Long parseLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String buildSessionKey(String userId, String deviceType, String deviceId) {
        return String.format("session:%s:%s:%s", userId, deviceType, deviceId);
    }
}