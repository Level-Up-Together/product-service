package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.global.security.TokenBlacklistChecker;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response.SessionsResponseDto.Session;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    // 이 창 안에서 previousRefreshToken 으로 재시도하면 현재 세션 토큰을 그대로 재전달한다.
    private static final long ROTATION_GRACE_MILLIS = Duration.ofMinutes(2).toMillis();

    // 세션 TTL 버퍼 — refresh 만료 직후 grace/시계 오차 케이스에서 세션이 먼저 사라지지 않게 한다
    private static final Duration SESSION_TTL_BUFFER = Duration.ofDays(1);

    public void saveTokensToRedis(String userId, String deviceType,
                                  String deviceId, String accessToken, String refreshToken) {

        String sessionKey = buildSessionKey(userId, deviceType, deviceId);

        // 세션 데이터 저장
        Map<String, String> sessionData = Map.of(
            "accessToken", accessToken,
            "refreshToken", refreshToken,
            "loginTime", String.valueOf(System.currentTimeMillis()),
            "deviceType", deviceType,
            "deviceId", deviceId,
            "userId", userId
        );

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

        // Access Token 업데이트
        redisTemplate.opsForHash().put(sessionKey, "accessToken", newAccessToken);

        String effectiveRefreshToken = newRefreshToken;
        if (newRefreshToken != null) {
            // rotation: 현재 토큰은 grace 재시도용 previous 로 보관하고,
            // 한 세대 전 previous 는 이 시점에 블랙리스트 처리한다 (동시 유효 토큰을 2개로 제한)
            String currentRefreshToken =
                (String) redisTemplate.opsForHash().get(sessionKey, "refreshToken");
            String outgoingPrevious =
                (String) redisTemplate.opsForHash().get(sessionKey, "previousRefreshToken");
            blacklistToken(outgoingPrevious);

            if (currentRefreshToken != null) {
                redisTemplate.opsForHash().put(sessionKey, "previousRefreshToken", currentRefreshToken);
                redisTemplate.opsForHash().put(sessionKey, "previousRefreshTime",
                    String.valueOf(System.currentTimeMillis()));
            }

            redisTemplate.opsForHash().put(sessionKey, "refreshToken", newRefreshToken);
            redisTemplate.opsForHash().put(sessionKey, "lastRefreshTime", String.valueOf(System.currentTimeMillis()));
            log.info("Refresh token renewed for user: {}, device: {}", userId, deviceId);
        } else {
            effectiveRefreshToken =
                (String) redisTemplate.opsForHash().get(sessionKey, "refreshToken");
        }

        // 세션 TTL 을 refresh 토큰 잔여 유효기간에 정렬 (토큰은 유효한데 세션만 소멸하는 상태 방지)
        Duration sessionTtl = sessionTtl(effectiveRefreshToken);
        redisTemplate.expire(sessionKey, sessionTtl);
        redisTemplate.expire("userSessions:" + userId, sessionTtl);
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
        if (previous == null || previousTimeRaw == null || !presentedToken.equals(previous)) {
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

    // 토큰을 블랙리스트에 추가
    public void blacklistToken(String token) {
        if (token == null) {
            return;
        }

        try {
            if (jwtUtil.validateToken(token)) {
                String jti = jwtUtil.getJtiFromToken(token);
                long remainingTime = jwtUtil.getRemainingTime(token);

                if (remainingTime > 0) {
                    redisTemplate.opsForValue().set("blacklist:" + jti, "revoked",
                        Duration.ofMillis(remainingTime));
                    log.debug("Token blacklisted: {}", jti);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist token: {}", e.getMessage());
        }
    }

    // 특정 디바이스 로그아웃
    public void logout(String userId, String deviceType, String deviceId) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);

        try {
            // 토큰들을 블랙리스트에 추가 (grace 용 previous 포함)
            String accessToken = (String) redisTemplate.opsForHash().get(sessionKey, "accessToken");
            String refreshToken = (String) redisTemplate.opsForHash().get(sessionKey, "refreshToken");
            String previousRefreshToken =
                (String) redisTemplate.opsForHash().get(sessionKey, "previousRefreshToken");

            blacklistToken(accessToken);
            blacklistToken(refreshToken);
            blacklistToken(previousRefreshToken);

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
                    String accessToken = (String) redisTemplate.opsForHash().get(sessionKey, "accessToken");
                    String refreshToken = (String) redisTemplate.opsForHash().get(sessionKey, "refreshToken");
                    String previousRefreshToken =
                        (String) redisTemplate.opsForHash().get(sessionKey, "previousRefreshToken");

                    blacklistToken(accessToken);
                    blacklistToken(refreshToken);
                    blacklistToken(previousRefreshToken);

                    redisTemplate.delete(sessionKey);
                }

                log.info("All devices logged out for user: {}, sessions: {}", userId, sessions.size());
            }

            redisTemplate.delete(userSessionsKey);

        } catch (Exception e) {
            log.error("Failed to logout all devices for user: {}", userId, e);
        }
    }

    public String getRefreshToken(String userId, String deviceType, String deviceId) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);
        return (String) redisTemplate.opsForHash().get(sessionKey, "refreshToken");
    }

    // 저장된 Access Token 조회
    public String getAccessToken(String userId, String deviceType, String deviceId) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);
        return (String) redisTemplate.opsForHash().get(sessionKey, "accessToken");
    }

    // 특정 세션의 모든 정보 조회
    public Map<String, Object> getSessionInfo(String userId, String deviceType, String deviceId) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);
        Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(sessionKey);

        Map<String, Object> result = new HashMap<>();
        sessionData.forEach((k, v) -> result.put(k.toString(), v));

        String refreshToken = (String) result.get("refreshToken");
        if (refreshToken != null) {
            try {
                result.put("refreshTokenRemaining", jwtUtil.getRemainingTime(refreshToken));
                result.put("shouldRenewRefreshToken", shouldRenewRefreshToken(refreshToken));
                result.put("refreshTokenValid", jwtUtil.validateToken(refreshToken));
                result.put("canRenewRefreshToken", canRenewRefreshToken(refreshToken));

            } catch (Exception e) {
                result.put("refreshTokenRemaining", 0);
                result.put("shouldRenewRefreshToken", false);
                result.put("refreshTokenValid", false);
                result.put("canRenewRefreshToken", false);
            }
        }

        return result;
    }

    // 사용자의 모든 활성 세션 조회
    public List<Session> getActiveSessions(String userId) {
        String userSessionsKey = "userSessions:" + userId;
        Set<String> sessions = redisTemplate.opsForSet().members(userSessionsKey);

        List<Session> activeSessions = new ArrayList<>();

        if (sessions != null) {
            for (String sessionKey : sessions) {
                Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(sessionKey);

                if (!sessionData.isEmpty()) {
                    Map<String, String> session = new HashMap<>();
                    sessionData.forEach((k, v) -> session.put(k.toString(), (String) v));

                    String refreshToken = session.get("refreshToken");
                    String accessToken = session.get("accessToken");

                    Session.SessionBuilder builder = Session.builder()
                        .deviceType(session.get("deviceType"))
                        .deviceId(session.get("deviceId"))
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .loginTime(session.get("loginTime"))
                        .memberId(session.get("userId"));

                    if (refreshToken != null) {
                        try {
                            builder.refreshTokenRemaining(java.math.BigInteger.valueOf(jwtUtil.getRemainingTime(refreshToken)));
                            builder.shouldRenew(shouldRenewRefreshToken(refreshToken));
                            builder.refreshTokenValid(jwtUtil.validateToken(refreshToken));
                        } catch (Exception e) {
                            builder.refreshTokenRemaining(java.math.BigInteger.ZERO);
                            builder.shouldRenew(false);
                            builder.refreshTokenValid(false);
                        }
                    }

                    if (accessToken != null) {
                        try {
                            builder.accessTokenRemaining(java.math.BigInteger.valueOf(jwtUtil.getRemainingTime(accessToken)));
                            builder.accessTokenValid(jwtUtil.validateToken(accessToken));
                        } catch (Exception e) {
                            builder.accessTokenRemaining(java.math.BigInteger.ZERO);
                            builder.accessTokenValid(false);
                        }
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

    // 특정 토큰으로 세션 정보 조회
    public Session getSessionByToken(String token) {
        try {
            String userId = jwtUtil.getEmailFromToken(token);
            String deviceId = jwtUtil.getDeviceIdFromToken(token);

            // 모든 활성 세션에서 해당 토큰을 가진 세션 찾기
            List<Session> sessions = getActiveSessions(userId);

            for (Session session : sessions) {
//                String sessionRefreshToken = (String) session.get("refreshToken");
//                String sessionAccessToken = (String) session.get("accessToken");

                String sessionRefreshToken = (String) session.getRefreshToken();
                String sessionAccessToken = (String) session.getAccessToken();

                if (token.equals(sessionRefreshToken) || token.equals(sessionAccessToken)) {
                    return session;
                }
            }

        } catch (Exception e) {
            log.warn("Failed to get session by token: {}", e.getMessage());
        }

        return (Session) Collections.emptyMap();
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
                String refreshToken = (String) redisTemplate.opsForHash()
                    .get(sessionKey, "refreshToken");

                if (refreshToken == null || !jwtUtil.validateToken(refreshToken)) {
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

    private String buildSessionKey(String userId, String deviceType, String deviceId) {
        return String.format("session:%s:%s:%s", userId, deviceType, deviceId);
    }
}
