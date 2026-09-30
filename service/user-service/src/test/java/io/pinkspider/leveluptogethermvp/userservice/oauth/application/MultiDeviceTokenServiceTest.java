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
    // LUT-336: 세션 키에서 deviceType 제거 (session:{userId}:{deviceId})
    private static final String SESSION_KEY = "session:" + TEST_USER_ID + ":" + DEVICE_ID;

    @BeforeEach
    void setUp() {
        multiDeviceTokenService = new MultiDeviceTokenService(
            redisTemplate, jwtUtil, slidingExpirationService, objectMapper
        );
        // LUT-336: resolveSessionKey 가 신 키 존재를 먼저 확인한다 — 구 키 이관 경로를 타지 않도록 고정
        org.mockito.Mockito.lenient().when(redisTemplate.hasKey(SESSION_KEY)).thenReturn(true);
        org.mockito.Mockito.lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        org.mockito.Mockito.lenient().when(setOperations.members(anyString())).thenReturn(Set.of());
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
            when(hashOperations.get(SESSION_KEY, "previousRefreshTime")).thenReturn(null);
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
            // LUT-336: grace window(2분) 밖이어야 블랙리스트 대상이 된다
            when(hashOperations.get(SESSION_KEY, "previousRefreshTime"))
                .thenReturn(String.valueOf(System.currentTimeMillis() - Duration.ofMinutes(5).toMillis()));
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
    @DisplayName("LUT-336: 세션 키 정합성 테스트")
    class SessionKeyCompatibilityTest {

        private static final String LEGACY_KEY =
            "session:" + TEST_USER_ID + ":ios:" + DEVICE_ID;
        private static final String USER_SESSIONS_KEY = "userSessions:" + TEST_USER_ID;

        @Test
        @DisplayName("구 형식(deviceType 포함) 키가 있으면 신 키로 이관한다")
        void migratesLegacyKey() {
            // given — 신 키는 없고 구 키만 남아 있는 배포 직후 상태
            when(redisTemplate.hasKey(SESSION_KEY)).thenReturn(false);
            when(redisTemplate.hasKey(LEGACY_KEY)).thenReturn(true);
            when(setOperations.members(USER_SESSIONS_KEY)).thenReturn(Set.of(LEGACY_KEY));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "loginTime")).thenReturn("1700000000000");

            // when
            multiDeviceTokenService.getLoginTime(TEST_USER_ID, "ios", DEVICE_ID);

            // then — RENAME 으로 TTL 을 보존하며 이관하고, 세션 목록도 신 키로 교체한다
            verify(redisTemplate).rename(LEGACY_KEY, SESSION_KEY);
            verify(setOperations).remove(USER_SESSIONS_KEY, LEGACY_KEY);
            verify(setOperations).add(USER_SESSIONS_KEY, SESSION_KEY);
        }

        @Test
        @DisplayName("앱(ios)이 만든 세션을 웹(web)으로 조회해도 같은 키를 본다")
        void appAndWebShareOneSession() {
            // given — 앱이 만든 구 키 하나만 존재 (prod LUT-336 재현)
            when(redisTemplate.hasKey(SESSION_KEY)).thenReturn(false, true);
            when(redisTemplate.hasKey(LEGACY_KEY)).thenReturn(true);
            when(setOperations.members(USER_SESSIONS_KEY)).thenReturn(Set.of(LEGACY_KEY));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "refreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));

            // when — deviceType 만 다르게 두 번 조회 (앱 → 웹)
            RefreshTokenMatch asApp = multiDeviceTokenService.checkRefreshToken(
                TEST_USER_ID, "ios", DEVICE_ID, REFRESH_TOKEN);
            RefreshTokenMatch asWeb = multiDeviceTokenService.checkRefreshToken(
                TEST_USER_ID, "web", DEVICE_ID, REFRESH_TOKEN);

            // then — 예전에는 웹이 NO_SESSION 을 받아 강제 로그아웃됐다
            assertThat(asApp).isEqualTo(RefreshTokenMatch.MATCH);
            assertThat(asWeb).isEqualTo(RefreshTokenMatch.MATCH);
        }

        @Test
        @DisplayName("grace window 안의 previous 는 rotation 이 다시 일어나도 폐기하지 않는다")
        void keepsPreviousWithinGrace() {
            // given — 1초 전 rotation 된 previous 가 남아 있는 상태에서 또 rotation
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "refreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            when(hashOperations.get(SESSION_KEY, "refreshJti")).thenReturn("current-jti");
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn("1893456000000");
            when(hashOperations.get(SESSION_KEY, "previousRefreshTime"))
                .thenReturn(String.valueOf(System.currentTimeMillis() - 1000L));
            when(jwtUtil.getJtiFromToken("new-refresh-token")).thenReturn("new-refresh-jti");
            when(jwtUtil.getRemainingTime("new-refresh-token"))
                .thenReturn(Duration.ofDays(90).toMillis());
            when(jwtUtil.getJtiFromToken("new-access-token")).thenReturn("new-access-jti");
            when(jwtUtil.getRemainingTime("new-access-token"))
                .thenReturn(Duration.ofHours(24).toMillis());

            // when
            multiDeviceTokenService.updateTokens(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", "new-refresh-token");

            // then — 다른 클라이언트가 아직 쥐고 있을 수 있으므로 blacklist 하지 않는다.
            // (예전에는 grace 가 시간이 아니라 rotation 횟수로 닫혀 2초 만에 폐기됐다)
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("rotation 락은 SETNX 로 잡고 해제 시 삭제한다")
        void rotationLock() {
            String lockKey = "lock:" + SESSION_KEY;
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(lockKey), eq("1"), any(Duration.class)))
                .thenReturn(true, false);

            assertThat(multiDeviceTokenService.tryLockSession(TEST_USER_ID, DEVICE_ID)).isTrue();
            assertThat(multiDeviceTokenService.tryLockSession(TEST_USER_ID, DEVICE_ID)).isFalse();

            multiDeviceTokenService.unlockSession(TEST_USER_ID, DEVICE_ID);
            verify(redisTemplate).delete(lockKey);
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

    @Nested
    @DisplayName("isWithinRotationGrace 경계 테스트")
    class IsWithinRotationGraceEdgeTest {

        @Test
        @DisplayName("제시 토큰이 null이면 Redis 조회 없이 false")
        void nullPresentedToken_returnsFalse() {
            assertThat(multiDeviceTokenService.isWithinRotationGrace(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, null)).isFalse();

            verify(redisTemplate, never()).opsForHash();
        }

        @Test
        @DisplayName("previous 해시는 있지만 previousRefreshTime이 없으면 false")
        void missingPreviousTime_returnsFalse() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "previousRefreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            when(hashOperations.get(SESSION_KEY, "previousRefreshTime")).thenReturn(null);

            assertThat(multiDeviceTokenService.isWithinRotationGrace(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, REFRESH_TOKEN)).isFalse();
        }

        @Test
        @DisplayName("previousRefreshTime이 숫자가 아니면 false")
        void corruptedPreviousTime_returnsFalse() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "previousRefreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            when(hashOperations.get(SESSION_KEY, "previousRefreshTime")).thenReturn("not-a-number");

            assertThat(multiDeviceTokenService.isWithinRotationGrace(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, REFRESH_TOKEN)).isFalse();
        }
    }

    @Nested
    @DisplayName("checkRefreshToken 경계 테스트")
    class CheckRefreshTokenEdgeTest {

        @Test
        @DisplayName("제시 토큰이 null이면 세션이 있어도 MISMATCH")
        void nullPresentedToken_returnsMismatch() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "refreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));

            assertThat(multiDeviceTokenService.checkRefreshToken(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, null))
                .isEqualTo(RefreshTokenMatch.MISMATCH);
        }
    }

    @Nested
    @DisplayName("blacklistToken jti 경계 테스트")
    class BlacklistJtiEdgeTest {

        @Test
        @DisplayName("jti가 null이면 블랙리스트에 추가하지 않는다")
        void nullJti_notAdded() {
            when(jwtUtil.validateToken(ACCESS_TOKEN)).thenReturn(true);
            when(jwtUtil.getJtiFromToken(ACCESS_TOKEN)).thenReturn(null);
            when(jwtUtil.getRemainingTime(ACCESS_TOKEN)).thenReturn(60_000L);

            multiDeviceTokenService.blacklistToken(ACCESS_TOKEN);

            verify(redisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("logoutAllDevices 경계 테스트")
    class LogoutAllDevicesEdgeTest {

        @Test
        @DisplayName("세션 목록이 빈 Set이면 세션 처리 없이 목록 키만 삭제한다")
        void emptySessions_onlyDeletesUserSessionsKey() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(new HashSet<>());

            multiDeviceTokenService.logoutAllDevices(TEST_USER_ID);

            verify(redisTemplate, never()).opsForHash();
            verify(redisTemplate).delete("userSessions:" + TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("getSessionInfo 갱신 판정 테스트")
    class GetSessionInfoRenewTest {

        private Map<Object, Object> sessionWithRefreshExp(long expiresAt) {
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("refreshExpiresAt", String.valueOf(expiresAt));
            sessionData.put("loginTime", String.valueOf(System.currentTimeMillis()));
            return sessionData;
        }

        @Test
        @DisplayName("refresh가 만료됐으면 valid/canRenew 모두 false이고 최대수명 판정은 하지 않는다")
        void expiredRefresh_notValid() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries(SESSION_KEY))
                .thenReturn(sessionWithRefreshExp(System.currentTimeMillis() - 1000L));

            Map<String, Object> result = multiDeviceTokenService.getSessionInfo(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            assertThat(result.get("refreshTokenValid")).isEqualTo(false);
            assertThat(result.get("canRenewRefreshToken")).isEqualTo(false);
            verify(slidingExpirationService, never()).isSessionWithinMaxLifetime(any());
        }

        @Test
        @DisplayName("갱신 시점이 아니면 canRenew는 false이고 최대수명 판정은 하지 않는다")
        void shouldNotRenew_cannotRenew() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries(SESSION_KEY))
                .thenReturn(sessionWithRefreshExp(
                    System.currentTimeMillis() + Duration.ofDays(60).toMillis()));
            when(slidingExpirationService.shouldRenewByRemainingMillis(
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(false);

            Map<String, Object> result = multiDeviceTokenService.getSessionInfo(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            assertThat(result.get("refreshTokenValid")).isEqualTo(true);
            assertThat(result.get("shouldRenewRefreshToken")).isEqualTo(false);
            assertThat(result.get("canRenewRefreshToken")).isEqualTo(false);
            verify(slidingExpirationService, never()).isSessionWithinMaxLifetime(any());
        }

        @Test
        @DisplayName("세션 절대 상한을 넘었으면 갱신 시점이어도 canRenew는 false")
        void beyondMaxLifetime_cannotRenew() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries(SESSION_KEY))
                .thenReturn(sessionWithRefreshExp(
                    System.currentTimeMillis() + Duration.ofDays(60).toMillis()));
            when(slidingExpirationService.shouldRenewByRemainingMillis(
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
            when(slidingExpirationService.isSessionWithinMaxLifetime(any())).thenReturn(false);

            Map<String, Object> result = multiDeviceTokenService.getSessionInfo(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            assertThat(result.get("shouldRenewRefreshToken")).isEqualTo(true);
            assertThat(result.get("canRenewRefreshToken")).isEqualTo(false);
        }

        @Test
        @DisplayName("레거시 평문 refresh 잔여시간 계산이 실패하면 0으로 취급한다")
        void legacyRefreshParseFailure_zeroRemaining() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("refreshToken", REFRESH_TOKEN);
            when(hashOperations.entries(SESSION_KEY)).thenReturn(sessionData);
            when(jwtUtil.getRemainingTime(REFRESH_TOKEN)).thenThrow(new RuntimeException("bad"));

            Map<String, Object> result = multiDeviceTokenService.getSessionInfo(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            assertThat(result.get("refreshTokenRemaining")).isEqualTo(0L);
            assertThat(result.get("refreshTokenValid")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("getActiveSessions 필드 경계 테스트")
    class GetActiveSessionsEdgeTest {

        private static final String KEY = "session:" + TEST_USER_ID + ":device1";

        private void stubSession(Map<Object, Object> sessionData) {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(Set.of(KEY));
            when(hashOperations.entries(KEY)).thenReturn(sessionData);
        }

        @Test
        @DisplayName("토큰 정보가 전혀 없으면 잔여시간 필드를 채우지 않고 문자열 필드는 null")
        void noTokenInfo_fieldsNull() {
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("userId", TEST_USER_ID);
            stubSession(sessionData);

            List<Session> result = multiDeviceTokenService.getActiveSessions(TEST_USER_ID);

            assertThat(result).hasSize(1);
            Session session = result.get(0);
            assertThat(session.getDeviceType()).isNull();
            assertThat(session.getRefreshTokenRemaining()).isNull();
            assertThat(session.getAccessTokenRemaining()).isNull();
            assertThat(session.isRefreshTokenValid()).isFalse();
            assertThat(session.isAccessTokenValid()).isFalse();
        }

        @Test
        @DisplayName("만료된 exp 메타데이터는 잔여 0과 valid=false로 표시한다")
        void expiredMetadata_invalid() {
            long past = System.currentTimeMillis() - Duration.ofHours(1).toMillis();
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("refreshExpiresAt", String.valueOf(past));
            sessionData.put("accessExpiresAt", String.valueOf(past));
            stubSession(sessionData);

            List<Session> result = multiDeviceTokenService.getActiveSessions(TEST_USER_ID);

            Session session = result.get(0);
            assertThat(session.getRefreshTokenRemaining()).isEqualTo(java.math.BigInteger.ZERO);
            assertThat(session.isRefreshTokenValid()).isFalse();
            assertThat(session.getAccessTokenRemaining()).isEqualTo(java.math.BigInteger.ZERO);
            assertThat(session.isAccessTokenValid()).isFalse();
        }

        @Test
        @DisplayName("레거시 평문 access 토큰은 원문에서 잔여시간을 계산한다")
        void legacyAccessToken_computedFromRaw() {
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("accessToken", "legacy-access");
            sessionData.put("refreshToken", REFRESH_TOKEN);
            stubSession(sessionData);
            when(jwtUtil.getRemainingTime("legacy-access")).thenReturn(5_000L);
            when(jwtUtil.getRemainingTime(REFRESH_TOKEN)).thenThrow(new RuntimeException("bad"));

            List<Session> result = multiDeviceTokenService.getActiveSessions(TEST_USER_ID);

            Session session = result.get(0);
            assertThat(session.getAccessTokenRemaining())
                .isEqualTo(java.math.BigInteger.valueOf(5_000L));
            assertThat(session.isAccessTokenValid()).isTrue();
            // 레거시 refresh 파싱 실패 → 0 취급
            assertThat(session.getRefreshTokenRemaining()).isEqualTo(java.math.BigInteger.ZERO);
            assertThat(session.isRefreshTokenValid()).isFalse();
        }

        @Test
        @DisplayName("레거시 평문 access 토큰 파싱이 실패하면 0으로 취급한다")
        void legacyAccessTokenParseFailure_zero() {
            Map<Object, Object> sessionData = new HashMap<>();
            sessionData.put("accessToken", "broken-access");
            stubSession(sessionData);
            when(jwtUtil.getRemainingTime("broken-access")).thenThrow(new RuntimeException("bad"));

            List<Session> result = multiDeviceTokenService.getActiveSessions(TEST_USER_ID);

            Session session = result.get(0);
            assertThat(session.getAccessTokenRemaining()).isEqualTo(java.math.BigInteger.ZERO);
            assertThat(session.isAccessTokenValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("getSessionStats 테스트")
    class GetSessionStatsTest {

        @SuppressWarnings("unchecked")
        private Map<String, Object> captureStats() {
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(objectMapper).convertValue(captor.capture(), eq(Session.class));
            return (Map<String, Object>) captor.getValue();
        }

        @Test
        @DisplayName("세션이 없으면 총 개수 0이고 최초/최근 로그인 시각을 넣지 않는다")
        void noSessions_noLoginTimes() {
            Session mapped = Session.builder().build();
            when(objectMapper.convertValue(any(), eq(Session.class))).thenReturn(mapped);

            Session result = multiDeviceTokenService.getSessionStats(TEST_USER_ID);

            assertThat(result).isSameAs(mapped);
            Map<String, Object> stats = captureStats();
            assertThat(stats.get("totalSessions")).isEqualTo(0);
            assertThat(stats).doesNotContainKeys("oldestLoginTime", "newestLoginTime");
        }

        @Test
        @DisplayName("세션이 있으면 가장 오래된/최근 로그인 시각과 디바이스별 개수를 계산한다")
        void withSessions_loginTimesComputed() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            String key1 = "session:" + TEST_USER_ID + ":d1";
            String key2 = "session:" + TEST_USER_ID + ":d2";
            when(setOperations.members("userSessions:" + TEST_USER_ID)).thenReturn(Set.of(key1, key2));

            Map<Object, Object> s1 = new HashMap<>();
            s1.put("deviceType", "ios");
            s1.put("loginTime", "1000");
            Map<Object, Object> s2 = new HashMap<>();
            s2.put("deviceType", "ios");
            s2.put("loginTime", "5000");
            when(hashOperations.entries(key1)).thenReturn(s1);
            when(hashOperations.entries(key2)).thenReturn(s2);
            when(objectMapper.convertValue(any(), eq(Session.class)))
                .thenReturn(Session.builder().build());

            multiDeviceTokenService.getSessionStats(TEST_USER_ID);

            Map<String, Object> stats = captureStats();
            assertThat(stats.get("totalSessions")).isEqualTo(2);
            assertThat(stats.get("oldestLoginTime")).isEqualTo(1000L);
            assertThat(stats.get("newestLoginTime")).isEqualTo(5000L);
            @SuppressWarnings("unchecked")
            Map<String, Long> deviceTypeCounts = (Map<String, Long>) stats.get("deviceTypeCounts");
            assertThat(deviceTypeCounts).containsEntry("ios", 2L);
        }
    }

    @Nested
    @DisplayName("cleanupExpiredSessions 경계 테스트")
    class CleanupExpiredSessionsEdgeTest {

        @Test
        @DisplayName("userId를 추출할 수 없는 키는 세션만 삭제하고 세션 목록은 건드리지 않는다")
        void keyWithoutUserId_deletedWithoutSetRemoval() {
            when(redisTemplate.keys("session:*")).thenReturn(Set.of("session:"));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get("session:", "refreshExpiresAt"))
                .thenReturn(String.valueOf(System.currentTimeMillis() - 1000L));

            assertThat(multiDeviceTokenService.cleanupExpiredSessions()).isEqualTo(1);

            verify(redisTemplate).delete("session:");
            verify(redisTemplate, never()).opsForSet();
        }

        @Test
        @DisplayName("레거시 평문 refresh가 아직 유효하면 삭제하지 않는다")
        void legacyValid_notDeleted() {
            String key = "session:user5:device5";
            when(redisTemplate.keys("session:*")).thenReturn(Set.of(key));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(key, "refreshExpiresAt")).thenReturn(null);
            when(hashOperations.get(key, "refreshToken")).thenReturn(REFRESH_TOKEN);
            when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(true);

            assertThat(multiDeviceTokenService.cleanupExpiredSessions()).isEqualTo(0);
            verify(redisTemplate, never()).delete(eq(key));
        }
    }

    @Nested
    @DisplayName("updateTokens TTL/메타데이터 경계 테스트")
    class UpdateTokensEdgeTest {

        private void stubNewAccess() {
            when(jwtUtil.getJtiFromToken("new-access-token")).thenReturn("new-access-jti");
            when(jwtUtil.getRemainingTime("new-access-token"))
                .thenReturn(Duration.ofHours(24).toMillis());
        }

        private void stubNewRefresh() {
            when(jwtUtil.getJtiFromToken("new-refresh-token")).thenReturn("new-refresh-jti");
            when(jwtUtil.getRemainingTime("new-refresh-token"))
                .thenReturn(Duration.ofDays(90).toMillis());
        }

        /** rotation 경로가 읽는 previous* 필드를 전부 없음으로 고정 (strict stubs) */
        private void stubNoPrevious() {
            when(hashOperations.get(SESSION_KEY, "previousRefreshTime")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "previousRefreshJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "previousRefreshExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "previousRefreshToken")).thenReturn(null);
        }

        @Test
        @DisplayName("access 메타데이터 추출이 실패하면 putAll 없이 레거시 필드만 제거하고 TTL은 버퍼만 적용한다")
        void accessMetadataFailure_noPutAll_bufferTtl() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(jwtUtil.getJtiFromToken("bad-access")).thenThrow(new RuntimeException("bad"));
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshToken")).thenReturn(null);

            multiDeviceTokenService.updateTokens(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "bad-access", null);

            verify(hashOperations, never()).putAll(eq(SESSION_KEY), any());
            verify(hashOperations).delete(SESSION_KEY, "accessToken");
            verify(redisTemplate).expire(eq(SESSION_KEY), eq(Duration.ofDays(1)));
        }

        @Test
        @DisplayName("저장된 refresh exp가 이미 지났으면 TTL은 버퍼만 적용한다")
        void storedRefreshExpired_bufferTtl() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            stubNewAccess();
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt"))
                .thenReturn(String.valueOf(System.currentTimeMillis() - 1000L));

            multiDeviceTokenService.updateTokens(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", null);

            verify(redisTemplate).expire(eq(SESSION_KEY), eq(Duration.ofDays(1)));
        }

        @Test
        @DisplayName("exp 메타데이터가 없고 레거시 평문 refresh면 원문 잔여시간으로 TTL을 계산한다")
        void legacyStoredRefresh_ttlFromRaw() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            stubNewAccess();
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshToken")).thenReturn(REFRESH_TOKEN);
            when(jwtUtil.getRemainingTime(REFRESH_TOKEN)).thenReturn(Duration.ofDays(10).toMillis());

            multiDeviceTokenService.updateTokens(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", null);

            verify(redisTemplate).expire(eq(SESSION_KEY), eq(Duration.ofDays(11)));
        }

        @Test
        @DisplayName("exp 메타데이터가 없고 해시 refresh면 TTL은 버퍼만 적용한다")
        void hashedStoredRefreshWithoutExp_bufferTtl() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            stubNewAccess();
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));

            multiDeviceTokenService.updateTokens(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", null);

            verify(redisTemplate).expire(eq(SESSION_KEY), eq(Duration.ofDays(1)));
            verify(jwtUtil, never()).getRemainingTime(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("rotation 시 현재 refresh 기록이 없으면 previous를 만들지 않는다")
        void rotationWithoutCurrent_noPrevious() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            stubNewAccess();
            stubNewRefresh();
            stubNoPrevious();
            when(hashOperations.get(SESSION_KEY, "refreshToken")).thenReturn(null);

            multiDeviceTokenService.updateTokens(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", "new-refresh-token");

            Map<String, String> stored = captureAllPutFields();
            assertThat(stored).doesNotContainKeys("previousRefreshToken", "previousRefreshTime");
            assertThat(stored.get("refreshJti")).isEqualTo("new-refresh-jti");
        }

        @Test
        @DisplayName("rotation 시 레거시 평문 현재 토큰은 원문에서 previous 메타데이터를 추출한다")
        void rotationLegacyCurrent_extractsMetadata() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            stubNewAccess();
            stubNewRefresh();
            stubNoPrevious();
            when(hashOperations.get(SESSION_KEY, "refreshToken")).thenReturn(REFRESH_TOKEN);
            when(hashOperations.get(SESSION_KEY, "refreshJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn(null);
            when(jwtUtil.getJtiFromToken(REFRESH_TOKEN)).thenReturn("legacy-jti");
            when(jwtUtil.getRemainingTime(REFRESH_TOKEN)).thenReturn(1_000L);

            multiDeviceTokenService.updateTokens(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", "new-refresh-token");

            Map<String, String> stored = captureAllPutFields();
            assertThat(stored.get("previousRefreshToken")).isEqualTo(REFRESH_TOKEN);
            assertThat(stored.get("previousRefreshJti")).isEqualTo("legacy-jti");
            assertThat(stored).containsKey("previousRefreshExpiresAt");
        }

        @Test
        @DisplayName("rotation 시 레거시 원문 메타데이터 추출이 실패하면 previous jti/exp 없이 저장한다")
        void rotationLegacyCurrent_metadataFailure() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            stubNewAccess();
            stubNewRefresh();
            stubNoPrevious();
            when(hashOperations.get(SESSION_KEY, "refreshToken")).thenReturn(REFRESH_TOKEN);
            when(hashOperations.get(SESSION_KEY, "refreshJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn(null);
            when(jwtUtil.getJtiFromToken(REFRESH_TOKEN)).thenThrow(new RuntimeException("bad"));

            multiDeviceTokenService.updateTokens(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", "new-refresh-token");

            Map<String, String> stored = captureAllPutFields();
            assertThat(stored.get("previousRefreshToken")).isEqualTo(REFRESH_TOKEN);
            assertThat(stored).doesNotContainKeys("previousRefreshJti", "previousRefreshExpiresAt");
        }

        @Test
        @DisplayName("rotation 시 해시 현재 토큰에 jti가 없으면 exp만 previous로 옮긴다")
        void rotationHashedCurrentWithoutJti_expOnly() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            stubNewAccess();
            stubNewRefresh();
            stubNoPrevious();
            when(hashOperations.get(SESSION_KEY, "refreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            when(hashOperations.get(SESSION_KEY, "refreshJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn("1893456000000");

            multiDeviceTokenService.updateTokens(
                TEST_USER_ID, DEVICE_TYPE, DEVICE_ID, "new-access-token", "new-refresh-token");

            Map<String, String> stored = captureAllPutFields();
            assertThat(stored).doesNotContainKey("previousRefreshJti");
            assertThat(stored.get("previousRefreshExpiresAt")).isEqualTo("1893456000000");
            verify(jwtUtil, never()).getJtiFromToken(REFRESH_TOKEN);
        }
    }

    @Nested
    @DisplayName("logout 블랙리스트 폴백 경계 테스트")
    class LogoutFallbackTest {

        @Test
        @DisplayName("previous jti는 있지만 exp가 없으면 레거시 평문 previous를 원문으로 블랙리스트한다")
        void previousMissingExp_fallsBackToRawPrevious() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            when(hashOperations.get(SESSION_KEY, "accessJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "accessExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "accessToken")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            when(hashOperations.get(SESSION_KEY, "previousRefreshJti")).thenReturn("prev-jti");
            when(hashOperations.get(SESSION_KEY, "previousRefreshExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "previousRefreshToken")).thenReturn("old-plain");
            when(jwtUtil.validateToken("old-plain")).thenReturn(true);
            when(jwtUtil.getJtiFromToken("old-plain")).thenReturn("old-jti");
            when(jwtUtil.getRemainingTime("old-plain")).thenReturn(60_000L);

            multiDeviceTokenService.logout(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            verify(valueOperations).set(eq("blacklist:old-jti"), eq("revoked"), any(Duration.class));
            verify(valueOperations, org.mockito.Mockito.times(1))
                .set(anyString(), anyString(), any(Duration.class));
            verify(redisTemplate).delete(SESSION_KEY);
        }

        @Test
        @DisplayName("jti/exp가 불완전하고 레거시 값이 해시면 어떤 토큰도 블랙리스트하지 않는다")
        void incompleteMetadataAndHashedLegacy_noBlacklist() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);

            when(hashOperations.get(SESSION_KEY, "accessJti")).thenReturn("access-jti");
            when(hashOperations.get(SESSION_KEY, "accessExpiresAt")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "accessToken")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "refreshExpiresAt")).thenReturn("1893456000000");
            when(hashOperations.get(SESSION_KEY, "refreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken(REFRESH_TOKEN));
            when(hashOperations.get(SESSION_KEY, "previousRefreshJti")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "previousRefreshExpiresAt")).thenReturn("1893456000000");
            when(hashOperations.get(SESSION_KEY, "previousRefreshToken"))
                .thenReturn(MultiDeviceTokenService.hashToken("old"));

            multiDeviceTokenService.logout(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            verify(redisTemplate, never()).opsForValue();
            verify(jwtUtil, never()).validateToken(anyString());
            verify(redisTemplate).delete(SESSION_KEY);
        }
    }

    @Nested
    @DisplayName("LUT-336: 구 키 이관 경계 테스트")
    class LegacyKeyMigrationEdgeTest {

        private static final String USER_SESSIONS_KEY = "userSessions:" + TEST_USER_ID;
        private static final String KEY_IOS = "session:" + TEST_USER_ID + ":ios:" + DEVICE_ID;
        private static final String KEY_WEB = "session:" + TEST_USER_ID + ":web:" + DEVICE_ID;
        private static final String KEY_ANDROID = "session:" + TEST_USER_ID + ":android:" + DEVICE_ID;
        private static final String KEY_DEAD = "session:" + TEST_USER_ID + ":ipad:" + DEVICE_ID;

        @Test
        @DisplayName("세션 목록이 null이면 이관 없이 신 키를 그대로 쓴다")
        void nullMembers_usesCanonical() {
            when(redisTemplate.hasKey(SESSION_KEY)).thenReturn(false);
            when(setOperations.members(USER_SESSIONS_KEY)).thenReturn(null);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(SESSION_KEY, "loginTime")).thenReturn("100");

            assertThat(multiDeviceTokenService.getLoginTime(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID))
                .isEqualTo(100L);
            verify(redisTemplate, never()).rename(anyString(), anyString());
        }

        @Test
        @DisplayName("요청 deviceType 키가 없으면 가장 최근 활동 키를 이관하고 나머지 중복은 정리한다")
        void picksMostRecentLegacyAndRemovesDuplicates() {
            when(redisTemplate.hasKey(SESSION_KEY)).thenReturn(false);
            when(setOperations.members(USER_SESSIONS_KEY)).thenReturn(Set.of(
                KEY_IOS, KEY_WEB, KEY_ANDROID, KEY_DEAD,
                "session:" + TEST_USER_ID + ":ios:other-device", // suffix 불일치
                "other:" + TEST_USER_ID + ":ios:" + DEVICE_ID,   // prefix 불일치
                SESSION_KEY));                                     // 신 키 자체는 제외
            when(redisTemplate.hasKey(KEY_IOS)).thenReturn(true);
            when(redisTemplate.hasKey(KEY_WEB)).thenReturn(true);
            when(redisTemplate.hasKey(KEY_ANDROID)).thenReturn(true);
            when(redisTemplate.hasKey(KEY_DEAD)).thenReturn(false);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            // lastRefreshTime 우선, 없으면 loginTime, 둘 다 없으면 0
            when(hashOperations.get(KEY_IOS, "lastRefreshTime")).thenReturn("2000");
            when(hashOperations.get(KEY_WEB, "lastRefreshTime")).thenReturn(null);
            when(hashOperations.get(KEY_WEB, "loginTime")).thenReturn("3000");
            when(hashOperations.get(KEY_ANDROID, "lastRefreshTime")).thenReturn(null);
            when(hashOperations.get(KEY_ANDROID, "loginTime")).thenReturn(null);
            when(hashOperations.get(SESSION_KEY, "loginTime")).thenReturn(null);

            // when — 요청 deviceType(mobile) 키는 목록에 없다
            multiDeviceTokenService.getLoginTime(TEST_USER_ID, DEVICE_TYPE, DEVICE_ID);

            // then — 가장 최근 활동(web, 3000)을 이관
            verify(redisTemplate).rename(KEY_WEB, SESSION_KEY);
            verify(setOperations).remove(USER_SESSIONS_KEY, KEY_WEB);
            verify(setOperations).add(USER_SESSIONS_KEY, SESSION_KEY);
            // 중복 구 키 정리
            verify(redisTemplate).delete(KEY_IOS);
            verify(redisTemplate).delete(KEY_ANDROID);
            verify(setOperations).remove(USER_SESSIONS_KEY, KEY_IOS);
            verify(setOperations).remove(USER_SESSIONS_KEY, KEY_ANDROID);
            verify(redisTemplate, never()).delete(KEY_WEB);
            verify(redisTemplate, never()).delete(KEY_DEAD);
        }

        @Test
        @DisplayName("이관(RENAME)이 실패하면 구 키를 그대로 사용한다")
        void renameFailure_fallsBackToLegacyKey() {
            when(redisTemplate.hasKey(SESSION_KEY)).thenReturn(false);
            when(redisTemplate.hasKey(KEY_IOS)).thenReturn(true);
            when(setOperations.members(USER_SESSIONS_KEY)).thenReturn(Set.of(KEY_IOS));
            org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(redisTemplate).rename(KEY_IOS, SESSION_KEY);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(KEY_IOS, "loginTime")).thenReturn("123");

            assertThat(multiDeviceTokenService.getLoginTime(TEST_USER_ID, "ios", DEVICE_ID))
                .isEqualTo(123L);
            verify(setOperations, never()).add(USER_SESSIONS_KEY, SESSION_KEY);
        }
    }
}
