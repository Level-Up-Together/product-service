package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.response.SessionsResponseDto.Session;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class MultiDeviceTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private SlidingExpirationService slidingExpirationService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MultiDeviceTokenService multiDeviceTokenService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String DEVICE_TYPE = "mobile";
    private static final String DEVICE_ID = "device-456";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @BeforeEach
    void setUp() {
        multiDeviceTokenService = new MultiDeviceTokenService(
            redisTemplate, jwtUtil, slidingExpirationService, objectMapper
        );
    }

    @Nested
    @DisplayName("saveTokensToRedis 테스트")
    class SaveTokensToRedisTest {

        @Test
        @DisplayName("토큰을 Redis에 저장한다")
        void saveTokensToRedis_success() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);

            // when
            multiDeviceTokenService.saveTokensToRedis(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, ACCESS_TOKEN, REFRESH_TOKEN);

            // then
            String expectedSessionKey = "session:" + TEST_USER_ID + ":" + DEVICE_TYPE + ":" + DEVICE_ID;
            verify(hashOperations).putAll(eq(expectedSessionKey), any(Map.class));
            verify(redisTemplate).expire(eq(expectedSessionKey), eq(Duration.ofDays(7)));
        }
    }

    @Nested
    @DisplayName("updateTokens 테스트")
    class UpdateTokensTest {

        @Test
        @DisplayName("액세스 토큰만 업데이트한다")
        void updateTokens_accessTokenOnly() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            // when
            multiDeviceTokenService.updateTokens(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", null);

            // then
            String expectedSessionKey = "session:" + TEST_USER_ID + ":" + DEVICE_TYPE + ":" + DEVICE_ID;
            verify(hashOperations).put(eq(expectedSessionKey), eq("accessToken"), eq("new-access-token"));
            verify(hashOperations, never()).put(eq(expectedSessionKey), eq("refreshToken"), anyString());
        }

        @Test
        @DisplayName("액세스 토큰과 리프레시 토큰 모두 업데이트한다")
        void updateTokens_bothTokens() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            // when
            multiDeviceTokenService.updateTokens(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", "new-refresh-token");

            // then
            String expectedSessionKey = "session:" + TEST_USER_ID + ":" + DEVICE_TYPE + ":" + DEVICE_ID;
            verify(hashOperations).put(eq(expectedSessionKey), eq("accessToken"), eq("new-access-token"));
            verify(hashOperations).put(eq(expectedSessionKey), eq("refreshToken"), eq("new-refresh-token"));
        }
    }

    @Nested
    @DisplayName("shouldRenewRefreshToken 테스트")
    class ShouldRenewRefreshTokenTest {

        @Test
        @DisplayName("SlidingExpirationService에 위임한다")
        void shouldRenewRefreshToken_delegatesToService() {
            // given
            when(slidingExpirationService.shouldRenewRefreshToken(REFRESH_TOKEN)).thenReturn(true);

            // when
            boolean result = multiDeviceTokenService.shouldRenewRefreshToken(REFRESH_TOKEN);

            // then
            assertThat(result).isTrue();
            verify(slidingExpirationService).shouldRenewRefreshToken(REFRESH_TOKEN);
        }
    }

    @Nested
    @DisplayName("canRenewRefreshToken 테스트")
    class CanRenewRefreshTokenTest {

        @Test
        @DisplayName("SlidingExpirationService에 위임한다")
        void canRenewRefreshToken_delegatesToService() {
            // given
            when(slidingExpirationService.canRenewToken(REFRESH_TOKEN)).thenReturn(true);

            // when
            boolean result = multiDeviceTokenService.canRenewRefreshToken(REFRESH_TOKEN);

            // then
            assertThat(result).isTrue();
            verify(slidingExpirationService).canRenewToken(REFRESH_TOKEN);
        }
    }

    @Nested
    @DisplayName("isTokenBlacklisted 테스트")
    class IsTokenBlacklistedTest {

        @Test
        @DisplayName("null 토큰이면 true를 반환한다")
        void isTokenBlacklisted_nullToken_returnsTrue() {
            // when
            boolean result = multiDeviceTokenService.isTokenBlacklisted(null);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("블랙리스트에 있으면 true를 반환한다")
        void isTokenBlacklisted_inBlacklist_returnsTrue() {
            // given
            String jti = "test-jti";
            when(jwtUtil.getJtiFromToken(ACCESS_TOKEN)).thenReturn(jti);
            when(redisTemplate.hasKey("blacklist:" + jti)).thenReturn(true);

            // when
            boolean result = multiDeviceTokenService.isTokenBlacklisted(ACCESS_TOKEN);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("블랙리스트에 없으면 false를 반환한다")
        void isTokenBlacklisted_notInBlacklist_returnsFalse() {
            // given
            String jti = "test-jti";
            when(jwtUtil.getJtiFromToken(ACCESS_TOKEN)).thenReturn(jti);
            when(redisTemplate.hasKey("blacklist:" + jti)).thenReturn(false);

            // when
            boolean result = multiDeviceTokenService.isTokenBlacklisted(ACCESS_TOKEN);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("예외 발생 시 true를 반환한다")
        void isTokenBlacklisted_exception_returnsTrue() {
            // given
            when(jwtUtil.getJtiFromToken(ACCESS_TOKEN)).thenThrow(new RuntimeException("Error"));

            // when
            boolean result = multiDeviceTokenService.isTokenBlacklisted(ACCESS_TOKEN);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("blacklistToken 테스트")
    class BlacklistTokenTest {

        @Test
        @DisplayName("null 토큰이면 아무 작업도 하지 않는다")
        void blacklistToken_nullToken_doesNothing() {
            // when
            multiDeviceTokenService.blacklistToken(null);

            // then
            verify(jwtUtil, never()).validateToken(anyString());
        }

        @Test
        @DisplayName("유효한 토큰을 블랙리스트에 추가한다")
        void blacklistToken_validToken_addsToBlacklist() {
            // given
            String jti = "test-jti";
            long remainingTime = 1000L * 60 * 60; // 1시간

            when(jwtUtil.validateToken(ACCESS_TOKEN)).thenReturn(true);
            when(jwtUtil.getJtiFromToken(ACCESS_TOKEN)).thenReturn(jti);
            when(jwtUtil.getRemainingTime(ACCESS_TOKEN)).thenReturn(remainingTime);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // when
            multiDeviceTokenService.blacklistToken(ACCESS_TOKEN);

            // then
            verify(valueOperations).set(eq("blacklist:" + jti), eq("revoked"), eq(Duration.ofMillis(remainingTime)));
        }
    }

    @Nested
    @DisplayName("logout 테스트")
    class LogoutTest {

        @Test
        @DisplayName("특정 디바이스에서 로그아웃한다")
        void logout_success() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);

            String sessionKey = "session:" + TEST_USER_ID + ":" + DEVICE_TYPE + ":" + DEVICE_ID;
            when(hashOperations.get(sessionKey, "accessToken")).thenReturn(ACCESS_TOKEN);
            when(hashOperations.get(sessionKey, "refreshToken")).thenReturn(REFRESH_TOKEN);

            // when
            multiDeviceTokenService.logout(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            // then
            verify(redisTemplate).delete(sessionKey);
            verify(setOperations).remove(eq("userSessions:" + TEST_USER_ID), eq(sessionKey));
        }
    }

    @Nested
    @DisplayName("logoutAllDevices 테스트")
    class LogoutAllDevicesTest {

        @Test
        @DisplayName("모든 디바이스에서 로그아웃한다")
        void logoutAllDevices_success() {
            // given
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Set<String> sessions = new HashSet<>();
            sessions.add("session:" + TEST_USER_ID + ":mobile:device1");
            sessions.add("session:" + TEST_USER_ID + ":web:device2");

            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(sessions);

            // when
            multiDeviceTokenService.logoutAllDevices(TEST_USER_ID);

            // then
            for (String session : sessions) {
                verify(redisTemplate).delete(session);
            }
            verify(redisTemplate).delete("userSessions:" + TEST_USER_ID);
        }

        @Test
        @DisplayName("세션이 없어도 예외가 발생하지 않는다")
        void logoutAllDevices_noSessions() {
            // given
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(null);

            // when
            multiDeviceTokenService.logoutAllDevices(TEST_USER_ID);

            // then
            verify(redisTemplate).delete("userSessions:" + TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("getRefreshToken 테스트")
    class GetRefreshTokenTest {

        @Test
        @DisplayName("리프레시 토큰을 조회한다")
        void getRefreshToken_success() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            String sessionKey = "session:" + TEST_USER_ID + ":" + DEVICE_TYPE + ":" + DEVICE_ID;
            when(hashOperations.get(sessionKey, "refreshToken")).thenReturn(REFRESH_TOKEN);

            // when
            String result = multiDeviceTokenService.getRefreshToken(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            // then
            assertThat(result).isEqualTo(REFRESH_TOKEN);
        }
    }

    @Nested
    @DisplayName("getAccessToken 테스트")
    class GetAccessTokenTest {

        @Test
        @DisplayName("액세스 토큰을 조회한다")
        void getAccessToken_success() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            String sessionKey = "session:" + TEST_USER_ID + ":" + DEVICE_TYPE + ":" + DEVICE_ID;
            when(hashOperations.get(sessionKey, "accessToken")).thenReturn(ACCESS_TOKEN);

            // when
            String result = multiDeviceTokenService.getAccessToken(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            // then
            assertThat(result).isEqualTo(ACCESS_TOKEN);
        }
    }

    @Nested
    @DisplayName("sessionExists 테스트")
    class SessionExistsTest {

        @Test
        @DisplayName("세션이 존재하면 true를 반환한다")
        void sessionExists_exists_returnsTrue() {
            // given
            String sessionKey = "session:" + TEST_USER_ID + ":" + DEVICE_TYPE + ":" + DEVICE_ID;
            when(redisTemplate.hasKey(sessionKey)).thenReturn(true);

            // when
            boolean result = multiDeviceTokenService.sessionExists(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("세션이 존재하지 않으면 false를 반환한다")
        void sessionExists_notExists_returnsFalse() {
            // given
            String sessionKey = "session:" + TEST_USER_ID + ":" + DEVICE_TYPE + ":" + DEVICE_ID;
            when(redisTemplate.hasKey(sessionKey)).thenReturn(false);

            // when
            boolean result = multiDeviceTokenService.sessionExists(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getActiveSessions 테스트")
    class GetActiveSessionsTest {

        @Test
        @DisplayName("세션이 없으면 빈 목록을 반환한다")
        void getActiveSessions_noSessions() {
            // given
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(null);

            // when
            List<Session> result = multiDeviceTokenService.getActiveSessions(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("cleanupExpiredSessions 테스트")
    class CleanupExpiredSessionsTest {

        @Test
        @DisplayName("만료된 세션이 없으면 0을 반환한다")
        void cleanupExpiredSessions_noExpired() {
            // given
            when(redisTemplate.keys("session:*")).thenReturn(null);

            // when
            int result = multiDeviceTokenService.cleanupExpiredSessions();

            // then
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("만료된 세션을 정리한다")
        void cleanupExpiredSessions_success() {
            // given
            Set<String> sessionKeys = new HashSet<>();
            sessionKeys.add("session:user1:mobile:device1");

            when(redisTemplate.keys("session:*")).thenReturn(sessionKeys);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(hashOperations.get("session:user1:mobile:device1", "refreshToken")).thenReturn(REFRESH_TOKEN);
            when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(false);

            // when
            int result = multiDeviceTokenService.cleanupExpiredSessions();

            // then
            assertThat(result).isEqualTo(1);
            verify(redisTemplate).delete("session:user1:mobile:device1");
        }

        @Test
        @DisplayName("refreshToken이 null이면 세션을 삭제한다")
        void cleanupExpiredSessions_nullRefreshToken() {
            // given
            Set<String> sessionKeys = new HashSet<>();
            sessionKeys.add("session:user2:web:device2");

            when(redisTemplate.keys("session:*")).thenReturn(sessionKeys);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(hashOperations.get("session:user2:web:device2", "refreshToken")).thenReturn(null);

            // when
            int result = multiDeviceTokenService.cleanupExpiredSessions();

            // then
            assertThat(result).isEqualTo(1);
            verify(redisTemplate).delete("session:user2:web:device2");
        }

        @Test
        @DisplayName("유효한 세션은 삭제하지 않는다")
        void cleanupExpiredSessions_validSession_notDeleted() {
            // given
            Set<String> sessionKeys = new HashSet<>();
            sessionKeys.add("session:user3:mobile:device3");

            when(redisTemplate.keys("session:*")).thenReturn(sessionKeys);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get("session:user3:mobile:device3", "refreshToken")).thenReturn(REFRESH_TOKEN);
            when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(true);

            // when
            int result = multiDeviceTokenService.cleanupExpiredSessions();

            // then
            assertThat(result).isEqualTo(0);
            verify(redisTemplate, never()).delete(eq("session:user3:mobile:device3"));
        }
    }

    @Nested
    @DisplayName("blacklistToken 추가 분기 테스트")
    class BlacklistTokenExtraTest {

        @Test
        @DisplayName("remainingTime이 0 이하이면 블랙리스트에 추가하지 않는다")
        void blacklistToken_zeroRemainingTime_notAdded() {
            // given
            String jti = "test-jti";

            when(jwtUtil.validateToken(ACCESS_TOKEN)).thenReturn(true);
            when(jwtUtil.getJtiFromToken(ACCESS_TOKEN)).thenReturn(jti);
            when(jwtUtil.getRemainingTime(ACCESS_TOKEN)).thenReturn(0L);

            // when
            multiDeviceTokenService.blacklistToken(ACCESS_TOKEN);

            // then
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("토큰 검증 실패 시 블랙리스트에 추가하지 않는다")
        void blacklistToken_invalidToken_notAdded() {
            // given
            when(jwtUtil.validateToken(ACCESS_TOKEN)).thenReturn(false);

            // when
            multiDeviceTokenService.blacklistToken(ACCESS_TOKEN);

            // then
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("예외 발생 시 로그만 남기고 종료한다")
        void blacklistToken_exception_doesNotThrow() {
            // given
            when(jwtUtil.validateToken(ACCESS_TOKEN)).thenThrow(new RuntimeException("JWT error"));

            // when & then
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> multiDeviceTokenService.blacklistToken(ACCESS_TOKEN));
        }
    }

    @Nested
    @DisplayName("getSessionInfo 테스트")
    class GetSessionInfoTest {

        @Test
        @DisplayName("refreshToken이 없으면 기본 세션 정보만 반환한다")
        void getSessionInfo_noRefreshToken() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            String sessionKey = "session:" + TEST_USER_ID + ":" + DEVICE_TYPE + ":" + DEVICE_ID;
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("accessToken", ACCESS_TOKEN);
            sessionData.put("userId", TEST_USER_ID);
            when(hashOperations.entries(sessionKey)).thenReturn(sessionData);

            // when
            Map<String, Object> result = multiDeviceTokenService.getSessionInfo(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            // then
            assertThat(result).containsKey("accessToken");
            assertThat(result).doesNotContainKey("refreshTokenRemaining");
        }

        @Test
        @DisplayName("refreshToken이 있으면 토큰 정보를 포함한 세션 정보를 반환한다")
        void getSessionInfo_withRefreshToken() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            String sessionKey = "session:" + TEST_USER_ID + ":" + DEVICE_TYPE + ":" + DEVICE_ID;
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("accessToken", ACCESS_TOKEN);
            sessionData.put("refreshToken", REFRESH_TOKEN);
            sessionData.put("userId", TEST_USER_ID);
            when(hashOperations.entries(sessionKey)).thenReturn(sessionData);
            when(jwtUtil.getRemainingTime(REFRESH_TOKEN)).thenReturn(86400000L);
            when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(true);
            when(slidingExpirationService.shouldRenewRefreshToken(REFRESH_TOKEN)).thenReturn(false);
            when(slidingExpirationService.canRenewToken(REFRESH_TOKEN)).thenReturn(true);

            // when
            Map<String, Object> result = multiDeviceTokenService.getSessionInfo(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            // then
            assertThat(result).containsKey("refreshTokenRemaining");
            assertThat(result).containsKey("shouldRenewRefreshToken");
            assertThat(result).containsKey("refreshTokenValid");
            assertThat(result).containsKey("canRenewRefreshToken");
        }

        @Test
        @DisplayName("refreshToken 처리 중 예외가 발생하면 기본값을 반환한다")
        void getSessionInfo_refreshTokenException() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            String sessionKey = "session:" + TEST_USER_ID + ":" + DEVICE_TYPE + ":" + DEVICE_ID;
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("refreshToken", REFRESH_TOKEN);
            when(hashOperations.entries(sessionKey)).thenReturn(sessionData);
            when(jwtUtil.getRemainingTime(REFRESH_TOKEN)).thenThrow(new RuntimeException("token error"));

            // when
            Map<String, Object> result = multiDeviceTokenService.getSessionInfo(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            // then
            assertThat(result.get("refreshTokenRemaining")).isEqualTo(0);
            assertThat(result.get("shouldRenewRefreshToken")).isEqualTo(false);
            assertThat(result.get("refreshTokenValid")).isEqualTo(false);
            assertThat(result.get("canRenewRefreshToken")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("getActiveSessions 추가 분기 테스트")
    class GetActiveSessionsExtraTest {

        @Test
        @DisplayName("세션 데이터가 비어있으면 해당 세션을 건너뛴다")
        void getActiveSessions_emptySessionData_skipped() {
            // given
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Set<String> sessions = new HashSet<>();
            sessions.add("session:" + TEST_USER_ID + ":mobile:device1");
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(sessions);
            when(hashOperations.entries("session:" + TEST_USER_ID + ":mobile:device1"))
                .thenReturn(new HashMap<>());

            // when
            List<Session> result = multiDeviceTokenService.getActiveSessions(TEST_USER_ID);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("refreshToken이 있고 accessToken도 있으면 모든 정보를 포함한다")
        void getActiveSessions_withBothTokens() {
            // given
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Set<String> sessions = new HashSet<>();
            String sessionKey = "session:" + TEST_USER_ID + ":mobile:device1";
            sessions.add(sessionKey);
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(sessions);

            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("deviceType", "mobile");
            sessionData.put("deviceId", "device1");
            sessionData.put("accessToken", ACCESS_TOKEN);
            sessionData.put("refreshToken", REFRESH_TOKEN);
            sessionData.put("loginTime", "1000000");
            sessionData.put("userId", TEST_USER_ID);
            when(hashOperations.entries(sessionKey)).thenReturn(sessionData);

            when(jwtUtil.getRemainingTime(REFRESH_TOKEN)).thenReturn(86400000L);
            when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(true);
            when(slidingExpirationService.shouldRenewRefreshToken(REFRESH_TOKEN)).thenReturn(false);
            when(jwtUtil.getRemainingTime(ACCESS_TOKEN)).thenReturn(3600000L);
            when(jwtUtil.validateToken(ACCESS_TOKEN)).thenReturn(true);

            // when
            List<Session> result = multiDeviceTokenService.getActiveSessions(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDeviceType()).isEqualTo("mobile");
        }

        @Test
        @DisplayName("refreshToken 예외 발생 시 기본값으로 처리한다")
        void getActiveSessions_refreshTokenException() {
            // given
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Set<String> sessions = new HashSet<>();
            String sessionKey = "session:" + TEST_USER_ID + ":mobile:device1";
            sessions.add(sessionKey);
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(sessions);

            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("deviceType", "mobile");
            sessionData.put("deviceId", "device1");
            sessionData.put("refreshToken", REFRESH_TOKEN);
            sessionData.put("loginTime", "1000000");
            sessionData.put("userId", TEST_USER_ID);
            when(hashOperations.entries(sessionKey)).thenReturn(sessionData);

            when(jwtUtil.getRemainingTime(REFRESH_TOKEN)).thenThrow(new RuntimeException("token error"));

            // when
            List<Session> result = multiDeviceTokenService.getActiveSessions(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRefreshTokenRemaining()).isEqualTo(java.math.BigInteger.ZERO);
        }

        @Test
        @DisplayName("accessToken 예외 발생 시 기본값으로 처리한다")
        void getActiveSessions_accessTokenException() {
            // given
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Set<String> sessions = new HashSet<>();
            String sessionKey = "session:" + TEST_USER_ID + ":mobile:device1";
            sessions.add(sessionKey);
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(sessions);

            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("deviceType", "mobile");
            sessionData.put("deviceId", "device1");
            sessionData.put("accessToken", ACCESS_TOKEN);
            sessionData.put("loginTime", "1000000");
            sessionData.put("userId", TEST_USER_ID);
            when(hashOperations.entries(sessionKey)).thenReturn(sessionData);

            when(jwtUtil.getRemainingTime(ACCESS_TOKEN)).thenThrow(new RuntimeException("token error"));

            // when
            List<Session> result = multiDeviceTokenService.getActiveSessions(TEST_USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAccessTokenRemaining()).isEqualTo(java.math.BigInteger.ZERO);
        }
    }
}
