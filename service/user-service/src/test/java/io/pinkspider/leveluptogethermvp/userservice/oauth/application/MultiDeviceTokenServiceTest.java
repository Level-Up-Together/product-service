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
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.MultiDeviceTokenService.RefreshTokenMatch;
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
import org.mockito.ArgumentCaptor;
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
    private static final String SESSION_KEY =
        "session:" + TEST_USER_ID + ":" + DEVICE_TYPE + ":" + DEVICE_ID;

    @BeforeEach
    void setUp() {
        multiDeviceTokenService = new MultiDeviceTokenService(
            redisTemplate, jwtUtil, slidingExpirationService, objectMapper
        );
    }

    /** putAll 로 저장된 모든 필드를 하나의 맵으로 합쳐 반환 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, String> captureAllPutFields() {
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations, org.mockito.Mockito.atLeastOnce())
            .putAll(eq(SESSION_KEY), captor.capture());
        Map<String, String> merged = new HashMap<>();
        for (Map map : captor.getAllValues()) {
            merged.putAll(map);
        }
        return merged;
    }

    @Nested
    @DisplayName("saveTokensToRedis 테스트")
    class SaveTokensToRedisTest {

        @Test
        @DisplayName("refresh 토큰을 해시로 저장하고 TTL을 refresh 잔여시간 + 버퍼로 설정한다")
        void saveTokensToRedis_success() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(jwtUtil.getRemainingTime(REFRESH_TOKEN)).thenReturn(Duration.ofDays(90).toMillis());
            when(jwtUtil.getJtiFromToken(REFRESH_TOKEN)).thenReturn("refresh-jti");
            when(jwtUtil.getRemainingTime(ACCESS_TOKEN)).thenReturn(Duration.ofHours(24).toMillis());
            when(jwtUtil.getJtiFromToken(ACCESS_TOKEN)).thenReturn("access-jti");

            // when
            multiDeviceTokenService.saveTokensToRedis(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, ACCESS_TOKEN, REFRESH_TOKEN);

            // then — 원문 대신 해시 + 메타데이터 저장 (QA-231)
            Map<String, String> stored = captureAllPutFields();
            assertThat(stored.get("refreshToken")).startsWith("sha256:");
            assertThat(stored.get("refreshToken")).isNotEqualTo(REFRESH_TOKEN);
            assertThat(stored.get("refreshJti")).isEqualTo("refresh-jti");
            assertThat(stored.get("accessJti")).isEqualTo("access-jti");
            assertThat(stored).doesNotContainKey("accessToken");

            // refresh 잔여 90일 + 버퍼 1일
            verify(redisTemplate).expire(eq(SESSION_KEY), eq(Duration.ofDays(91)));
            verify(redisTemplate).expire(eq("userSessions:" + TEST_USER_ID), eq(Duration.ofDays(91)));
        }

        @Test
        @DisplayName("refresh 잔여시간을 읽지 못하면 버퍼(1일)만 적용한다")
        void saveTokensToRedis_fallbackTtlWhenRemainingUnavailable() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(jwtUtil.getRemainingTime(anyString())).thenThrow(new RuntimeException("parse error"));
            when(jwtUtil.getJtiFromToken(anyString())).thenReturn("some-jti");

            // when
            multiDeviceTokenService.saveTokensToRedis(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, ACCESS_TOKEN, REFRESH_TOKEN);

            // then
            verify(redisTemplate).expire(eq(SESSION_KEY), eq(Duration.ofDays(1)));
        }
    }

    @Nested
    @DisplayName("updateTokens 테스트")
    class UpdateTokensTest {

        @Test
        @DisplayName("액세스 토큰 메타데이터만 갱신하고 TTL은 저장된 refresh exp 기준으로 연장한다")
        void updateTokens_accessTokenOnly() {
            // given
            long refreshExpiresAt = System.currentTimeMillis() + Duration.ofDays(30).toMillis();
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt"))
                .thenReturn(String.valueOf(refreshExpiresAt));
            when(jwtUtil.getJtiFromToken("new-access-token")).thenReturn("new-access-jti");
            when(jwtUtil.getRemainingTime("new-access-token")).thenReturn(Duration.ofHours(24).toMillis());

            // when
            multiDeviceTokenService.updateTokens(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", null);

            // then
            Map<String, String> stored = captureAllPutFields();
            assertThat(stored.get("accessJti")).isEqualTo("new-access-jti");
            assertThat(stored).doesNotContainKey("refreshToken");
            // 레거시 평문 access 필드 제거
            verify(hashOperations).delete(SESSION_KEY, "accessToken");

            ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(redisTemplate).expire(eq(SESSION_KEY), ttlCaptor.capture());
            // exp 잔여(~30일) + 버퍼 1일
            assertThat(ttlCaptor.getValue().toMillis())
                .isBetween(Duration.ofDays(30).toMillis(), Duration.ofDays(31).toMillis() + 1000);
        }

        @Test
        @DisplayName("rotation 시 현재 해시를 previous로 이동하고 새 refresh 해시를 저장한다")
        void updateTokens_rotation() {
            // given
            String currentHash = MultiDeviceTokenService.hashToken(REFRESH_TOKEN);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "refreshToken")).thenReturn(currentHash);
            when(hashOperations.get(SESSION_KEY, "refreshJti")).thenReturn("current-jti");
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn("1893456000000");
            when(hashOperations.get(SESSION_KEY, "previousRefreshJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "previousRefreshExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "previousRefreshToken")).thenReturn(null);
            when(jwtUtil.getJtiFromToken("new-refresh-token")).thenReturn("new-refresh-jti");
            when(jwtUtil.getRemainingTime("new-refresh-token")).thenReturn(Duration.ofDays(90).toMillis());
            when(jwtUtil.getJtiFromToken("new-access-token")).thenReturn("new-access-jti");
            when(jwtUtil.getRemainingTime("new-access-token")).thenReturn(Duration.ofHours(24).toMillis());

            // when
            multiDeviceTokenService.updateTokens(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", "new-refresh-token");

            // then
            Map<String, String> stored = captureAllPutFields();
            assertThat(stored.get("previousRefreshToken")).isEqualTo(currentHash);
            assertThat(stored.get("previousRefreshJti")).isEqualTo("current-jti");
            assertThat(stored.get("refreshToken"))
                .isEqualTo(MultiDeviceTokenService.hashToken("new-refresh-token"));
            assertThat(stored.get("refreshJti")).isEqualTo("new-refresh-jti");
            assertThat(stored).containsKey("previousRefreshTime");

            verify(redisTemplate).expire(eq(SESSION_KEY), eq(Duration.ofDays(91)));
        }

        @Test
        @DisplayName("rotation 시 한 세대 전 previous는 jti로 블랙리스트 처리한다")
        void updateTokens_blacklistsOutgoingPreviousByJti() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "refreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            when(hashOperations.get(SESSION_KEY, "refreshJti")).thenReturn("current-jti");
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn("1893456000000");
            when(hashOperations.get(SESSION_KEY, "previousRefreshJti")).thenReturn("prev-jti");
            when(hashOperations.get(SESSION_KEY, "previousRefreshExpiresAt"))
                .thenReturn(String.valueOf(System.currentTimeMillis() + 1000L));
            when(jwtUtil.getJtiFromToken("new-refresh-token")).thenReturn("new-refresh-jti");
            when(jwtUtil.getRemainingTime("new-refresh-token")).thenReturn(Duration.ofDays(90).toMillis());
            when(jwtUtil.getJtiFromToken("new-access-token")).thenReturn("new-access-jti");
            when(jwtUtil.getRemainingTime("new-access-token")).thenReturn(Duration.ofHours(24).toMillis());
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // when
            multiDeviceTokenService.updateTokens(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", "new-refresh-token");

            // then
            verify(valueOperations).set(eq("blacklist:prev-jti"), eq("revoked"), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("updateTokensForGraceRetry 테스트")
    class UpdateTokensForGraceRetryTest {

        @Test
        @DisplayName("현재 토큰만 교체하고 previous 기록은 유지한다")
        void graceRetry_keepsPreviousRecord() {
            // given
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(jwtUtil.getJtiFromToken("retry-refresh-token")).thenReturn("retry-refresh-jti");
            when(jwtUtil.getRemainingTime("retry-refresh-token")).thenReturn(Duration.ofDays(90).toMillis());
            when(jwtUtil.getJtiFromToken("retry-access-token")).thenReturn("retry-access-jti");
            when(jwtUtil.getRemainingTime("retry-access-token")).thenReturn(Duration.ofHours(24).toMillis());

            // when
            multiDeviceTokenService.updateTokensForGraceRetry(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "retry-access-token", "retry-refresh-token");

            // then
            Map<String, String> stored = captureAllPutFields();
            assertThat(stored.get("refreshToken"))
                .isEqualTo(MultiDeviceTokenService.hashToken("retry-refresh-token"));
            // previous 필드는 건드리지 않는다 (반복 재시도 허용)
            assertThat(stored).doesNotContainKey("previousRefreshToken");
            assertThat(stored).doesNotContainKey("previousRefreshTime");

            verify(redisTemplate).expire(eq(SESSION_KEY), eq(Duration.ofDays(91)));
        }
    }

    @Nested
    @DisplayName("checkRefreshToken 테스트")
    class CheckRefreshTokenTest {

        @Test
        @DisplayName("해시 저장값과 일치하면 MATCH")
        void hashedMatch() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "refreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));

            assertThat(multiDeviceTokenService.checkRefreshToken(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, REFRESH_TOKEN))
                .isEqualTo(RefreshTokenMatch.MATCH);
        }

        @Test
        @DisplayName("레거시 평문 저장값과도 일치하면 MATCH (하위 호환)")
        void legacyPlaintextMatch() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "refreshToken")).thenReturn(REFRESH_TOKEN);

            assertThat(multiDeviceTokenService.checkRefreshToken(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, REFRESH_TOKEN))
                .isEqualTo(RefreshTokenMatch.MATCH);
        }

        @Test
        @DisplayName("다른 토큰이면 MISMATCH")
        void mismatch() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "refreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken("other-token"));

            assertThat(multiDeviceTokenService.checkRefreshToken(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, REFRESH_TOKEN))
                .isEqualTo(RefreshTokenMatch.MISMATCH);
        }

        @Test
        @DisplayName("세션이 없으면 NO_SESSION")
        void noSession() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "refreshToken")).thenReturn(null);

            assertThat(multiDeviceTokenService.checkRefreshToken(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, REFRESH_TOKEN))
                .isEqualTo(RefreshTokenMatch.NO_SESSION);
        }
    }

    @Nested
    @DisplayName("isWithinRotationGrace 테스트")
    class IsWithinRotationGraceTest {

        @Test
        @DisplayName("previous 해시와 일치하고 grace window 이내면 true")
        void withinGrace_returnsTrue() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "previousRefreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            when(hashOperations.get(SESSION_KEY, "previousRefreshTime"))
                .thenReturn(String.valueOf(System.currentTimeMillis() - 1000L));

            assertThat(multiDeviceTokenService.isWithinRotationGrace(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, REFRESH_TOKEN)).isTrue();
        }

        @Test
        @DisplayName("grace window를 지났으면 false")
        void expiredGrace_returnsFalse() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "previousRefreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            when(hashOperations.get(SESSION_KEY, "previousRefreshTime"))
                .thenReturn(String.valueOf(System.currentTimeMillis() - Duration.ofMinutes(3).toMillis()));

            assertThat(multiDeviceTokenService.isWithinRotationGrace(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, REFRESH_TOKEN)).isFalse();
        }

        @Test
        @DisplayName("previous와 일치하지 않으면 false")
        void tokenMismatch_returnsFalse() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "previousRefreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken("other-token"));

            assertThat(multiDeviceTokenService.isWithinRotationGrace(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, REFRESH_TOKEN)).isFalse();
        }

        @Test
        @DisplayName("previous 기록이 없으면 false")
        void noPrevious_returnsFalse() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "previousRefreshToken")).thenReturn(null);

            assertThat(multiDeviceTokenService.isWithinRotationGrace(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, REFRESH_TOKEN)).isFalse();
        }
    }

    @Nested
    @DisplayName("getLoginTime 테스트")
    class GetLoginTimeTest {

        @Test
        @DisplayName("저장된 loginTime을 반환한다")
        void getLoginTime_returnsValue() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "loginTime")).thenReturn("1713000000000");

            assertThat(multiDeviceTokenService.getLoginTime(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID))
                .isEqualTo(1713000000000L);
        }

        @Test
        @DisplayName("세션이 없으면 null을 반환한다")
        void getLoginTime_returnsNullWhenMissing() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "loginTime")).thenReturn(null);

            assertThat(multiDeviceTokenService.getLoginTime(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID)).isNull();
        }

        @Test
        @DisplayName("값이 손상됐으면 null을 반환한다")
        void getLoginTime_returnsNullWhenCorrupted() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "loginTime")).thenReturn("not-a-number");

            assertThat(multiDeviceTokenService.getLoginTime(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID)).isNull();
        }
    }

    @Nested
    @DisplayName("shouldRenew/canRenew 위임 테스트")
    class DelegationTest {

        @Test
        @DisplayName("shouldRenewRefreshToken은 SlidingExpirationService에 위임한다")
        void shouldRenewRefreshToken_delegatesToService() {
            when(slidingExpirationService.shouldRenewRefreshToken(REFRESH_TOKEN)).thenReturn(true);

            assertThat(multiDeviceTokenService.shouldRenewRefreshToken(REFRESH_TOKEN)).isTrue();
            verify(slidingExpirationService).shouldRenewRefreshToken(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("canRenewRefreshToken은 SlidingExpirationService에 위임한다")
        void canRenewRefreshToken_delegatesToService() {
            when(slidingExpirationService.canRenewToken(REFRESH_TOKEN)).thenReturn(true);

            assertThat(multiDeviceTokenService.canRenewRefreshToken(REFRESH_TOKEN)).isTrue();
            verify(slidingExpirationService).canRenewToken(REFRESH_TOKEN);
        }
    }

    @Nested
    @DisplayName("isTokenBlacklisted 테스트")
    class IsTokenBlacklistedTest {

        @Test
        @DisplayName("null 토큰이면 true를 반환한다")
        void isTokenBlacklisted_nullToken_returnsTrue() {
            assertThat(multiDeviceTokenService.isTokenBlacklisted(null)).isTrue();
        }

        @Test
        @DisplayName("블랙리스트에 있으면 true를 반환한다")
        void isTokenBlacklisted_inBlacklist_returnsTrue() {
            String jti = "test-jti";
            when(jwtUtil.getJtiFromToken(ACCESS_TOKEN)).thenReturn(jti);
            when(redisTemplate.hasKey("blacklist:" + jti)).thenReturn(true);

            assertThat(multiDeviceTokenService.isTokenBlacklisted(ACCESS_TOKEN)).isTrue();
        }

        @Test
        @DisplayName("블랙리스트에 없으면 false를 반환한다")
        void isTokenBlacklisted_notInBlacklist_returnsFalse() {
            String jti = "test-jti";
            when(jwtUtil.getJtiFromToken(ACCESS_TOKEN)).thenReturn(jti);
            when(redisTemplate.hasKey("blacklist:" + jti)).thenReturn(false);

            assertThat(multiDeviceTokenService.isTokenBlacklisted(ACCESS_TOKEN)).isFalse();
        }

        @Test
        @DisplayName("예외 발생 시 true를 반환한다")
        void isTokenBlacklisted_exception_returnsTrue() {
            when(jwtUtil.getJtiFromToken(ACCESS_TOKEN)).thenThrow(new RuntimeException("Error"));

            assertThat(multiDeviceTokenService.isTokenBlacklisted(ACCESS_TOKEN)).isTrue();
        }
    }

    @Nested
    @DisplayName("blacklistToken 테스트")
    class BlacklistTokenTest {

        @Test
        @DisplayName("null 토큰이면 아무 작업도 하지 않는다")
        void blacklistToken_nullToken_doesNothing() {
            multiDeviceTokenService.blacklistToken(null);

            verify(jwtUtil, never()).validateToken(anyString());
        }

        @Test
        @DisplayName("유효한 토큰을 블랙리스트에 추가한다")
        void blacklistToken_validToken_addsToBlacklist() {
            String jti = "test-jti";
            long remainingTime = 1000L * 60 * 60;

            when(jwtUtil.validateToken(ACCESS_TOKEN)).thenReturn(true);
            when(jwtUtil.getJtiFromToken(ACCESS_TOKEN)).thenReturn(jti);
            when(jwtUtil.getRemainingTime(ACCESS_TOKEN)).thenReturn(remainingTime);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            multiDeviceTokenService.blacklistToken(ACCESS_TOKEN);

            ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(valueOperations).set(eq("blacklist:" + jti), eq("revoked"), ttlCaptor.capture());
            // jti 블랙리스트는 exp 시각 기준으로 계산되므로 수 ms 오차 허용
            assertThat(ttlCaptor.getValue().toMillis())
                .isBetween(remainingTime - 1000, remainingTime + 1000);
        }

        @Test
        @DisplayName("remainingTime이 0 이하이면 블랙리스트에 추가하지 않는다")
        void blacklistToken_zeroRemainingTime_notAdded() {
            when(jwtUtil.validateToken(ACCESS_TOKEN)).thenReturn(true);
            when(jwtUtil.getJtiFromToken(ACCESS_TOKEN)).thenReturn("test-jti");
            when(jwtUtil.getRemainingTime(ACCESS_TOKEN)).thenReturn(0L);

            multiDeviceTokenService.blacklistToken(ACCESS_TOKEN);

            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("토큰 검증 실패 시 블랙리스트에 추가하지 않는다")
        void blacklistToken_invalidToken_notAdded() {
            when(jwtUtil.validateToken(ACCESS_TOKEN)).thenReturn(false);

            multiDeviceTokenService.blacklistToken(ACCESS_TOKEN);

            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("예외 발생 시 로그만 남기고 종료한다")
        void blacklistToken_exception_doesNotThrow() {
            when(jwtUtil.validateToken(ACCESS_TOKEN)).thenThrow(new RuntimeException("JWT error"));

            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> multiDeviceTokenService.blacklistToken(ACCESS_TOKEN));
        }
    }

    @Nested
    @DisplayName("logout 테스트")
    class LogoutTest {

        @Test
        @DisplayName("해시 세션은 jti 메타데이터로 블랙리스트 후 세션을 삭제한다")
        void logout_hashedSession() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            long futureExp = System.currentTimeMillis() + Duration.ofHours(1).toMillis();
            when(hashOperations.get(SESSION_KEY, "accessJti")).thenReturn("access-jti");
            when(hashOperations.get(SESSION_KEY, "accessExpiresAt")).thenReturn(String.valueOf(futureExp));
            when(hashOperations.get(SESSION_KEY, "refreshJti")).thenReturn("refresh-jti");
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn(String.valueOf(futureExp));
            when(hashOperations.get(SESSION_KEY, "previousRefreshJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "previousRefreshExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "previousRefreshToken")).thenReturn(null);

            multiDeviceTokenService.logout(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            verify(valueOperations).set(eq("blacklist:access-jti"), eq("revoked"), any(Duration.class));
            verify(valueOperations).set(eq("blacklist:refresh-jti"), eq("revoked"), any(Duration.class));
            verify(redisTemplate).delete(SESSION_KEY);
            verify(setOperations).remove(eq("userSessions:" + TEST_USER_ID), eq(SESSION_KEY));
        }

        @Test
        @DisplayName("레거시 평문 세션은 원문으로 블랙리스트 후 세션을 삭제한다")
        void logout_legacySession() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);

            when(hashOperations.get(SESSION_KEY, "accessJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "accessExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "accessToken")).thenReturn(ACCESS_TOKEN);
            when(hashOperations.get(SESSION_KEY, "refreshJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshToken")).thenReturn(REFRESH_TOKEN);
            when(hashOperations.get(SESSION_KEY, "previousRefreshJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "previousRefreshExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "previousRefreshToken")).thenReturn(null);
            when(jwtUtil.validateToken(anyString())).thenReturn(false); // 만료 취급 — 블랙리스트 생략 경로

            multiDeviceTokenService.logout(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            verify(redisTemplate).delete(SESSION_KEY);
            verify(setOperations).remove(eq("userSessions:" + TEST_USER_ID), eq(SESSION_KEY));
        }
    }

    @Nested
    @DisplayName("logoutAllDevices 테스트")
    class LogoutAllDevicesTest {

        @Test
        @DisplayName("모든 디바이스에서 로그아웃한다")
        void logoutAllDevices_success() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Set<String> sessions = new HashSet<>();
            sessions.add("session:" + TEST_USER_ID + ":mobile:device1");
            sessions.add("session:" + TEST_USER_ID + ":web:device2");

            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(sessions);

            multiDeviceTokenService.logoutAllDevices(TEST_USER_ID);

            for (String session : sessions) {
                verify(redisTemplate).delete(session);
            }
            verify(redisTemplate).delete("userSessions:" + TEST_USER_ID);
        }

        @Test
        @DisplayName("세션이 없어도 예외가 발생하지 않는다")
        void logoutAllDevices_noSessions() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(null);

            multiDeviceTokenService.logoutAllDevices(TEST_USER_ID);

            verify(redisTemplate).delete("userSessions:" + TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("sessionExists 테스트")
    class SessionExistsTest {

        @Test
        @DisplayName("세션이 존재하면 true를 반환한다")
        void sessionExists_exists_returnsTrue() {
            when(redisTemplate.hasKey(SESSION_KEY)).thenReturn(true);

            assertThat(multiDeviceTokenService.sessionExists(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID)).isTrue();
        }

        @Test
        @DisplayName("세션이 존재하지 않으면 false를 반환한다")
        void sessionExists_notExists_returnsFalse() {
            when(redisTemplate.hasKey(SESSION_KEY)).thenReturn(false);

            assertThat(multiDeviceTokenService.sessionExists(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("getSessionInfo 테스트")
    class GetSessionInfoTest {

        @Test
        @DisplayName("토큰 원문/해시는 응답에 포함하지 않는다 (QA-231)")
        void getSessionInfo_excludesTokenValues() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("refreshToken", MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            sessionData.put("previousRefreshToken", MultiDeviceTokenService.hashToken("old"));
            sessionData.put("accessToken", ACCESS_TOKEN); // 레거시 필드
            sessionData.put("userId", TEST_USER_ID);
            sessionData.put("refreshExpiresAt",
                String.valueOf(System.currentTimeMillis() + Duration.ofDays(60).toMillis()));
            when(hashOperations.entries(SESSION_KEY)).thenReturn(sessionData);

            Map<String, Object> result = multiDeviceTokenService.getSessionInfo(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            assertThat(result).doesNotContainKeys("refreshToken", "previousRefreshToken", "accessToken");
            assertThat(result).containsKey("userId");
        }

        @Test
        @DisplayName("해시 세션은 exp 메타데이터로 갱신 판정값을 계산한다")
        void getSessionInfo_hashedSession_computedFromMetadata() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("refreshToken", MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            sessionData.put("refreshExpiresAt",
                String.valueOf(System.currentTimeMillis() + Duration.ofDays(60).toMillis()));
            sessionData.put("loginTime", String.valueOf(System.currentTimeMillis()));
            when(hashOperations.entries(SESSION_KEY)).thenReturn(sessionData);
            when(slidingExpirationService.shouldRenewByRemainingMillis(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
            when(slidingExpirationService.isSessionWithinMaxLifetime(any())).thenReturn(true);

            Map<String, Object> result = multiDeviceTokenService.getSessionInfo(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            assertThat(result.get("refreshTokenValid")).isEqualTo(true);
            assertThat(result.get("shouldRenewRefreshToken")).isEqualTo(true);
            assertThat(result.get("canRenewRefreshToken")).isEqualTo(true);
            assertThat((Long) result.get("refreshTokenRemaining")).isPositive();
        }

        @Test
        @DisplayName("refresh 정보가 없으면 판정값을 포함하지 않는다")
        void getSessionInfo_noRefreshInfo() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("userId", TEST_USER_ID);
            when(hashOperations.entries(SESSION_KEY)).thenReturn(sessionData);

            Map<String, Object> result = multiDeviceTokenService.getSessionInfo(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            assertThat(result).doesNotContainKey("refreshTokenRemaining");
        }

        @Test
        @DisplayName("레거시 평문 세션은 원문에서 잔여시간을 계산한다 (하위 호환)")
        void getSessionInfo_legacySession() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("refreshToken", REFRESH_TOKEN); // 평문
            when(hashOperations.entries(SESSION_KEY)).thenReturn(sessionData);
            when(jwtUtil.getRemainingTime(REFRESH_TOKEN)).thenReturn(86400000L);
            when(slidingExpirationService.shouldRenewByRemainingMillis(86400000L)).thenReturn(false);

            Map<String, Object> result = multiDeviceTokenService.getSessionInfo(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            assertThat(result.get("refreshTokenRemaining")).isEqualTo(86400000L);
            assertThat(result.get("refreshTokenValid")).isEqualTo(true);
            assertThat(result).doesNotContainKey("refreshToken");
        }
    }

    @Nested
    @DisplayName("getActiveSessions 테스트")
    class GetActiveSessionsTest {

        @Test
        @DisplayName("세션이 없으면 빈 목록을 반환한다")
        void getActiveSessions_noSessions() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(null);

            List<Session> result = multiDeviceTokenService.getActiveSessions(TEST_USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("세션 데이터가 비어있으면 해당 세션을 건너뛴다")
        void getActiveSessions_emptySessionData_skipped() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Set<String> sessions = new HashSet<>();
            sessions.add("session:" + TEST_USER_ID + ":mobile:device1");
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(sessions);
            when(hashOperations.entries("session:" + TEST_USER_ID + ":mobile:device1"))
                .thenReturn(new HashMap<>());

            List<Session> result = multiDeviceTokenService.getActiveSessions(TEST_USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("해시 세션은 exp 메타데이터 기반으로 상태를 계산하고 토큰 값은 응답에 없다")
        void getActiveSessions_hashedSession() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Set<String> sessions = new HashSet<>();
            String sessionKey = "session:" + TEST_USER_ID + ":mobile:device1";
            sessions.add(sessionKey);
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(sessions);

            long now = System.currentTimeMillis();
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("deviceType", "mobile");
            sessionData.put("deviceId", "device1");
            sessionData.put("refreshToken", MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            sessionData.put("refreshExpiresAt", String.valueOf(now + Duration.ofDays(60).toMillis()));
            sessionData.put("accessExpiresAt", String.valueOf(now + Duration.ofHours(1).toMillis()));
            sessionData.put("loginTime", "1000000");
            sessionData.put("userId", TEST_USER_ID);
            when(hashOperations.entries(sessionKey)).thenReturn(sessionData);
            when(slidingExpirationService.shouldRenewByRemainingMillis(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(false);

            List<Session> result = multiDeviceTokenService.getActiveSessions(TEST_USER_ID);

            assertThat(result).hasSize(1);
            Session session = result.get(0);
            assertThat(session.getDeviceType()).isEqualTo("mobile");
            assertThat(session.getRefreshToken()).isNull();
            assertThat(session.getAccessToken()).isNull();
            assertThat(session.isRefreshTokenValid()).isTrue();
            assertThat(session.isAccessTokenValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("cleanupExpiredSessions 테스트")
    class CleanupExpiredSessionsTest {

        @Test
        @DisplayName("세션 키가 없으면 0을 반환한다")
        void cleanupExpiredSessions_noKeys() {
            when(redisTemplate.keys("session:*")).thenReturn(null);

            assertThat(multiDeviceTokenService.cleanupExpiredSessions()).isEqualTo(0);
        }

        @Test
        @DisplayName("exp 메타데이터가 만료된 해시 세션을 정리한다")
        void cleanupExpiredSessions_expiredByMetadata() {
            Set<String> sessionKeys = new HashSet<>();
            sessionKeys.add("session:user1:mobile:device1");

            when(redisTemplate.keys("session:*")).thenReturn(sessionKeys);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(hashOperations.get("session:user1:mobile:device1", "refreshExpiresAt"))
                .thenReturn(String.valueOf(System.currentTimeMillis() - 1000L));

            assertThat(multiDeviceTokenService.cleanupExpiredSessions()).isEqualTo(1);
            verify(redisTemplate).delete("session:user1:mobile:device1");
        }

        @Test
        @DisplayName("exp 메타데이터가 유효한 세션은 삭제하지 않는다")
        void cleanupExpiredSessions_validByMetadata_notDeleted() {
            Set<String> sessionKeys = new HashSet<>();
            sessionKeys.add("session:user3:mobile:device3");

            when(redisTemplate.keys("session:*")).thenReturn(sessionKeys);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get("session:user3:mobile:device3", "refreshExpiresAt"))
                .thenReturn(String.valueOf(System.currentTimeMillis() + Duration.ofDays(30).toMillis()));

            assertThat(multiDeviceTokenService.cleanupExpiredSessions()).isEqualTo(0);
            verify(redisTemplate, never()).delete(eq("session:user3:mobile:device3"));
        }

        @Test
        @DisplayName("레거시 평문 세션은 원문 검증으로 만료를 판정한다")
        void cleanupExpiredSessions_legacyExpired() {
            Set<String> sessionKeys = new HashSet<>();
            sessionKeys.add("session:user1:mobile:device1");

            when(redisTemplate.keys("session:*")).thenReturn(sessionKeys);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(hashOperations.get("session:user1:mobile:device1", "refreshExpiresAt")).thenReturn(null);
            when(hashOperations.get("session:user1:mobile:device1", "refreshToken")).thenReturn(REFRESH_TOKEN);
            when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(false);

            assertThat(multiDeviceTokenService.cleanupExpiredSessions()).isEqualTo(1);
            verify(redisTemplate).delete("session:user1:mobile:device1");
        }

        @Test
        @DisplayName("refreshToken이 없으면 세션을 삭제한다")
        void cleanupExpiredSessions_nullRefreshToken() {
            Set<String> sessionKeys = new HashSet<>();
            sessionKeys.add("session:user2:web:device2");

            when(redisTemplate.keys("session:*")).thenReturn(sessionKeys);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(hashOperations.get("session:user2:web:device2", "refreshExpiresAt")).thenReturn(null);
            when(hashOperations.get("session:user2:web:device2", "refreshToken")).thenReturn(null);

            assertThat(multiDeviceTokenService.cleanupExpiredSessions()).isEqualTo(1);
            verify(redisTemplate).delete("session:user2:web:device2");
        }

        @Test
        @DisplayName("해시인데 exp 메타데이터가 없으면 TTL에 맡기고 삭제하지 않는다")
        void cleanupExpiredSessions_hashedWithoutMetadata_kept() {
            Set<String> sessionKeys = new HashSet<>();
            sessionKeys.add("session:user4:mobile:device4");

            when(redisTemplate.keys("session:*")).thenReturn(sessionKeys);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get("session:user4:mobile:device4", "refreshExpiresAt")).thenReturn(null);
            when(hashOperations.get("session:user4:mobile:device4", "refreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));

            assertThat(multiDeviceTokenService.cleanupExpiredSessions()).isEqualTo(0);
            verify(redisTemplate, never()).delete(eq("session:user4:mobile:device4"));
        }
    }

    @Nested
    @DisplayName("hashToken 테스트")
    class HashTokenTest {

        @Test
        @DisplayName("sha256: prefix가 붙은 64자리 hex를 생성한다")
        void hashToken_format() {
            String hashed = MultiDeviceTokenService.hashToken(REFRESH_TOKEN);

            assertThat(hashed).startsWith("sha256:");
            assertThat(hashed.substring("sha256:".length())).hasSize(64).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("같은 입력은 같은 해시, 다른 입력은 다른 해시를 생성한다")
        void hashToken_deterministic() {
            assertThat(MultiDeviceTokenService.hashToken(REFRESH_TOKEN))
                .isEqualTo(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            assertThat(MultiDeviceTokenService.hashToken(REFRESH_TOKEN))
                .isNotEqualTo(MultiDeviceTokenService.hashToken("other-token"));
        }
    }
}
