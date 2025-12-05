package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.userservice.core.util.JwtUtil;
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
public class MultiDeviceTokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final SlidingExpirationService slidingExpirationService;
    private final ObjectMapper objectMapper;

    // Refresh Token 갱신 임계값 (예: 3일 남았을 때)
    private static final long REFRESH_RENEWAL_THRESHOLD = Duration.ofDays(3).toMillis();

    public void saveTokensToRedis(String userId, String deviceType,
                                  String deviceId, String accessToken, String refreshToken) {

        String sessionKey = buildSessionKey(userId, deviceType, deviceId);

        // 세션 데이터 저장
        Map<String, String> sessionData = Map.of(
            "access_token", accessToken,
            "refresh_token", refreshToken,
            "login_time", String.valueOf(System.currentTimeMillis()),
            "device_type", deviceType,
            "device_id", deviceId,
            "user_id", userId
        );

        redisTemplate.opsForHash().putAll(sessionKey, sessionData);
        redisTemplate.expire(sessionKey, Duration.ofDays(7));

        // 사용자별 활성 세션 목록 관리
        String userSessionsKey = "user_sessions:" + userId;
        redisTemplate.opsForSet().add(userSessionsKey, sessionKey);
        redisTemplate.expire(userSessionsKey, Duration.ofDays(7));
    }

    // 기존 토큰들을 업데이트 (Access Token은 항상, Refresh Token은 선택적)
    public void updateTokens(String userId, String deviceType, String deviceId,
                             String newAccessToken, String newRefreshToken) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);

        // Access Token 업데이트
        redisTemplate.opsForHash().put(sessionKey, "access_token", newAccessToken);

        // Refresh Token이 제공된 경우에만 업데이트
        if (newRefreshToken != null) {
            redisTemplate.opsForHash().put(sessionKey, "refresh_token", newRefreshToken);
            redisTemplate.opsForHash().put(sessionKey, "last_refresh_time", String.valueOf(System.currentTimeMillis()));
            log.info("Refresh token renewed for user: {}, device: {}", userId, deviceId);
        }

        // 세션 만료시간 연장
        redisTemplate.expire(sessionKey, Duration.ofDays(30));
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
            // 토큰들을 블랙리스트에 추가
            String accessToken = (String) redisTemplate.opsForHash().get(sessionKey, "access_token");
            String refreshToken = (String) redisTemplate.opsForHash().get(sessionKey, "refresh_token");

            blacklistToken(accessToken);
            blacklistToken(refreshToken);

            // 세션 삭제
            redisTemplate.delete(sessionKey);
            redisTemplate.opsForSet().remove("user_sessions:" + userId, sessionKey);

            log.info("User logged out: {}, device: {}", userId, deviceId);

        } catch (Exception e) {
            log.error("Failed to logout user: {}, device: {}", userId, deviceId, e);
        }
    }

    // 모든 디바이스에서 로그아웃
    public void logoutAllDevices(String userId) {
        String userSessionsKey = "user_sessions:" + userId;

        try {
            Set<String> sessions = redisTemplate.opsForSet().members(userSessionsKey);

            if (sessions != null && !sessions.isEmpty()) {
                for (String sessionKey : sessions) {
                    String accessToken = (String) redisTemplate.opsForHash().get(sessionKey, "access_token");
                    String refreshToken = (String) redisTemplate.opsForHash().get(sessionKey, "refresh_token");

                    blacklistToken(accessToken);
                    blacklistToken(refreshToken);

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
        return (String) redisTemplate.opsForHash().get(sessionKey, "refresh_token");
    }

    // 저장된 Access Token 조회
    public String getAccessToken(String userId, String deviceType, String deviceId) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);
        return (String) redisTemplate.opsForHash().get(sessionKey, "access_token");
    }

    // 특정 세션의 모든 정보 조회
    public Map<String, Object> getSessionInfo(String userId, String deviceType, String deviceId) {
        String sessionKey = buildSessionKey(userId, deviceType, deviceId);
        Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(sessionKey);

        Map<String, Object> result = new HashMap<>();
        sessionData.forEach((k, v) -> result.put(k.toString(), v));

        String refreshToken = (String) result.get("refresh_token");
        if (refreshToken != null) {
            try {
                result.put("refresh_token_remaining", jwtUtil.getRemainingTime(refreshToken));
                result.put("should_renew_refresh_token", shouldRenewRefreshToken(refreshToken));
                result.put("refresh_token_valid", jwtUtil.validateToken(refreshToken));
                result.put("can_renew_refresh_token", canRenewRefreshToken(refreshToken));

            } catch (Exception e) {
                result.put("refresh_token_remaining", 0);
                result.put("should_renew_refresh_token", false);
                result.put("refresh_token_valid", false);
                result.put("can_renew_refresh_token", false);
            }
        }

        return result;
    }

    // 사용자의 모든 활성 세션 조회
    public List<Session> getActiveSessions(String userId) {
        String userSessionsKey = "user_sessions:" + userId;
        Set<String> sessions = redisTemplate.opsForSet().members(userSessionsKey);

        List<Session> activeSessions = new ArrayList<>();

        if (sessions != null) {
            for (String sessionKey : sessions) {
                Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(sessionKey);

                if (!sessionData.isEmpty()) {
                    Map<String, Object> session = new HashMap<>();
                    sessionData.forEach((k, v) -> session.put(k.toString(), v));

                    // 추가 정보 계산
                    String refreshToken = (String) session.get("refresh_token");
                    String accessToken = (String) session.get("access_token");

                    if (refreshToken != null) {
                        try {
                            long remainingTime = jwtUtil.getRemainingTime(refreshToken);
                            session.put("refresh_token_remaining", remainingTime);
                            session.put("should_renew", shouldRenewRefreshToken(refreshToken));
                            session.put("refresh_token_valid", jwtUtil.validateToken(refreshToken));
                        } catch (Exception e) {
                            session.put("refresh_token_remaining", 0);
                            session.put("should_renew", false);
                            session.put("refresh_token_valid", false);
                        }
                    }

                    if (accessToken != null) {
                        try {
                            session.put("access_token_remaining", jwtUtil.getRemainingTime(accessToken));
                            session.put("access_token_valid", jwtUtil.validateToken(accessToken));
                        } catch (Exception e) {
                            session.put("access_token_remaining", 0);
                            session.put("access_token_valid", false);
                        }
                    }
                    Session result = objectMapper.convertValue(session, Session.class);
                    activeSessions.add(result);
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
        stats.put("total_sessions", sessions.size());

        // 디바이스 타입별 세션 수
        Map<String, Long> deviceTypeCount = sessions.stream()
            .collect(Collectors.groupingBy(
//                session -> (String) session.get("deviceType"),
                session -> session.getDeviceType(),
                Collectors.counting()
            ));
        stats.put("device_type_counts", deviceTypeCount);

        // 가장 오래된 세션과 가장 최근 세션
        if (!sessions.isEmpty()) {
            OptionalLong oldestLogin = sessions.stream()
//                .mapToLong(session -> Long.parseLong((String) session.get("loginTime")))
                .mapToLong(session -> Long.parseLong(session.getLoginTime()))
                .min();

            OptionalLong newestLogin = sessions.stream()
//                .mapToLong(session -> Long.parseLong((String) session.get("loginTime")))
                .mapToLong(session -> Long.parseLong(session.getLoginTime()))
                .max();

            if (oldestLogin.isPresent()) {
                stats.put("oldest_login_time", oldestLogin.getAsLong());
            }
            if (newestLogin.isPresent()) {
                stats.put("newest_login_time", newestLogin.getAsLong());
            }
        }

//        return stats;
        return objectMapper.convertValue(stats, Session.class);
    }

    // 만료된 세션 정리 (스케줄러에서 사용)
    public int cleanupExpiredSessions() {
        Set<String> sessionKeys = redisTemplate.keys("session:*");
        int cleanedCount = 0;

        if (sessionKeys != null) {
            for (String sessionKey : sessionKeys) {
                String refreshToken = (String) redisTemplate.opsForHash()
                    .get(sessionKey, "refresh_token");

                if (refreshToken == null || !jwtUtil.validateToken(refreshToken)) {
                    // 세션에서 사용자 ID 추출하여 user_sessions에서도 제거
                    String[] parts = sessionKey.split(":");
                    if (parts.length >= 2) {
                        String userId = parts[1];
                        redisTemplate.opsForSet().remove("user_sessions:" + userId, sessionKey);
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
