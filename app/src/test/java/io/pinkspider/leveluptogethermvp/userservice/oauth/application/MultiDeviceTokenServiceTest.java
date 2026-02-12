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
    }
}
