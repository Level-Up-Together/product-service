package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.pinkspider.leveluptogethermvp.userservice.core.properties.JwtProperties;
import io.pinkspider.global.security.JwtUtil;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlidingExpirationServiceTest {

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private JwtUtil jwtUtil;

    private SlidingExpirationService slidingExpirationService;

    private static final String TEST_TOKEN = "test-refresh-token";

    @BeforeEach
    void setUp() {
        slidingExpirationService = new SlidingExpirationService(jwtProperties, jwtUtil);
    }

    @Nested
    @DisplayName("shouldRenewRefreshToken 테스트")
    class ShouldRenewRefreshTokenTest {

        @Test
        @DisplayName("null 토큰이면 false를 반환한다")
        void shouldRenewRefreshToken_nullToken_returnsFalse() {
            // when
            boolean result = slidingExpirationService.shouldRenewRefreshToken(null);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("남은 시간이 임계값보다 작으면 true를 반환한다")
        void shouldRenewRefreshToken_belowThreshold_returnsTrue() {
            // given
            long remainingTime = 1000L * 60 * 60 * 24; // 1일
            long threshold = 1000L * 60 * 60 * 24 * 3; // 3일

            when(jwtUtil.getRemainingTime(TEST_TOKEN)).thenReturn(remainingTime);
            when(jwtProperties.getRenewalThresholdMillis()).thenReturn(threshold);

            // when
            boolean result = slidingExpirationService.shouldRenewRefreshToken(TEST_TOKEN);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("남은 시간이 임계값보다 크면 false를 반환한다")
        void shouldRenewRefreshToken_aboveThreshold_returnsFalse() {
            // given
            long remainingTime = 1000L * 60 * 60 * 24 * 5; // 5일
            long threshold = 1000L * 60 * 60 * 24 * 3; // 3일

            when(jwtUtil.getRemainingTime(TEST_TOKEN)).thenReturn(remainingTime);
            when(jwtProperties.getRenewalThresholdMillis()).thenReturn(threshold);

            // when
            boolean result = slidingExpirationService.shouldRenewRefreshToken(TEST_TOKEN);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("남은 시간이 0 이하이면 false를 반환한다")
        void shouldRenewRefreshToken_expired_returnsFalse() {
            // given
            when(jwtUtil.getRemainingTime(TEST_TOKEN)).thenReturn(0L);

            // when
            boolean result = slidingExpirationService.shouldRenewRefreshToken(TEST_TOKEN);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("예외 발생 시 false를 반환한다")
        void shouldRenewRefreshToken_exception_returnsFalse() {
            // given
            when(jwtUtil.getRemainingTime(TEST_TOKEN)).thenThrow(new RuntimeException("Token error"));

            // when
            boolean result = slidingExpirationService.shouldRenewRefreshToken(TEST_TOKEN);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("isWithinMaxLifetime 테스트")
    class IsWithinMaxLifetimeTest {

        @Test
        @DisplayName("최대 수명 내에 있으면 true를 반환한다")
        void isWithinMaxLifetime_withinLimit_returnsTrue() {
            // given
            Claims claims = mock(Claims.class);
            when(claims.getIssuedAt()).thenReturn(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24)); // 1일 전
            when(jwtUtil.getClaimsFromToken(TEST_TOKEN)).thenReturn(claims);
            when(jwtProperties.getMaxLifetimeMillis()).thenReturn(1000L * 60 * 60 * 24 * 30); // 30일

            // when
            boolean result = slidingExpirationService.isWithinMaxLifetime(TEST_TOKEN);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("최대 수명을 초과하면 false를 반환한다")
        void isWithinMaxLifetime_exceedsLimit_returnsFalse() {
            // given
            Claims claims = mock(Claims.class);
            when(claims.getIssuedAt()).thenReturn(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 31)); // 31일 전
            when(jwtUtil.getClaimsFromToken(TEST_TOKEN)).thenReturn(claims);
            when(jwtProperties.getMaxLifetimeMillis()).thenReturn(1000L * 60 * 60 * 24 * 30); // 30일

            // when
            boolean result = slidingExpirationService.isWithinMaxLifetime(TEST_TOKEN);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("예외 발생 시 false를 반환한다")
        void isWithinMaxLifetime_exception_returnsFalse() {
            // given
            when(jwtUtil.getClaimsFromToken(TEST_TOKEN)).thenThrow(new RuntimeException("Token error"));

            // when
            boolean result = slidingExpirationService.isWithinMaxLifetime(TEST_TOKEN);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("calculateRemainingMaxLifetime 테스트")
    class CalculateRemainingMaxLifetimeTest {

        @Test
        @DisplayName("남은 최대 수명을 계산한다")
        void calculateRemainingMaxLifetime_success() {
            // given
            long tokenAge = 1000L * 60 * 60 * 24 * 10; // 10일
            long maxLifetime = 1000L * 60 * 60 * 24 * 30; // 30일

            Claims claims = mock(Claims.class);
            when(claims.getIssuedAt()).thenReturn(new Date(System.currentTimeMillis() - tokenAge));
            when(jwtUtil.getClaimsFromToken(TEST_TOKEN)).thenReturn(claims);
            when(jwtProperties.getMaxLifetimeMillis()).thenReturn(maxLifetime);

            // when
            long result = slidingExpirationService.calculateRemainingMaxLifetime(TEST_TOKEN);

            // then
            assertThat(result).isGreaterThan(0);
            assertThat(result).isLessThanOrEqualTo(maxLifetime - tokenAge);
        }

        @Test
        @DisplayName("최대 수명을 초과하면 0을 반환한다")
        void calculateRemainingMaxLifetime_expired_returnsZero() {
            // given
            Claims claims = mock(Claims.class);
            when(claims.getIssuedAt()).thenReturn(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 31)); // 31일 전
            when(jwtUtil.getClaimsFromToken(TEST_TOKEN)).thenReturn(claims);
            when(jwtProperties.getMaxLifetimeMillis()).thenReturn(1000L * 60 * 60 * 24 * 30); // 30일

            // when
            long result = slidingExpirationService.calculateRemainingMaxLifetime(TEST_TOKEN);

            // then
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("예외 발생 시 0을 반환한다")
        void calculateRemainingMaxLifetime_exception_returnsZero() {
            // given
            when(jwtUtil.getClaimsFromToken(TEST_TOKEN)).thenThrow(new RuntimeException("Token error"));

            // when
            long result = slidingExpirationService.calculateRemainingMaxLifetime(TEST_TOKEN);

            // then
            assertThat(result).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("canRenewToken 테스트")
    class CanRenewTokenTest {

        @Test
        @DisplayName("갱신 가능하면 true를 반환한다")
        void canRenewToken_canRenew_returnsTrue() {
            // given
            Claims claims = mock(Claims.class);
            when(claims.getIssuedAt()).thenReturn(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24)); // 1일 전
            when(jwtUtil.getClaimsFromToken(TEST_TOKEN)).thenReturn(claims);
            when(jwtProperties.getMaxLifetimeMillis()).thenReturn(1000L * 60 * 60 * 24 * 30); // 30일

            when(jwtUtil.getRemainingTime(TEST_TOKEN)).thenReturn(1000L * 60 * 60 * 24 * 2); // 2일 남음
            when(jwtProperties.getRenewalThresholdMillis()).thenReturn(1000L * 60 * 60 * 24 * 3); // 3일

            // when
            boolean result = slidingExpirationService.canRenewToken(TEST_TOKEN);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("최대 수명을 초과하면 false를 반환한다")
        void canRenewToken_exceedsMaxLifetime_returnsFalse() {
            // given
            Claims claims = mock(Claims.class);
            when(claims.getIssuedAt()).thenReturn(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 31)); // 31일 전
            when(jwtUtil.getClaimsFromToken(TEST_TOKEN)).thenReturn(claims);
            when(jwtProperties.getMaxLifetimeMillis()).thenReturn(1000L * 60 * 60 * 24 * 30); // 30일

            // when
            boolean result = slidingExpirationService.canRenewToken(TEST_TOKEN);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getTokenRenewalInfo 테스트")
    class GetTokenRenewalInfoTest {

        @Test
        @DisplayName("토큰 갱신 정보를 반환한다")
        void getTokenRenewalInfo_success() {
            // given
            long remainingTime = 1000L * 60 * 60 * 24 * 2; // 2일

            Claims claims = mock(Claims.class);
            when(claims.getIssuedAt()).thenReturn(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24)); // 1일 전
            when(jwtUtil.getClaimsFromToken(TEST_TOKEN)).thenReturn(claims);
            when(jwtUtil.getRemainingTime(TEST_TOKEN)).thenReturn(remainingTime);
            when(jwtProperties.getMaxLifetimeMillis()).thenReturn(1000L * 60 * 60 * 24 * 30);
            when(jwtProperties.getRenewalThresholdMillis()).thenReturn(1000L * 60 * 60 * 24 * 3);

            // when
            Map<String, Object> result = slidingExpirationService.getTokenRenewalInfo(TEST_TOKEN);

            // then
            assertThat(result).containsKey("remaining_time");
            assertThat(result).containsKey("remaining_max_lifetime");
            assertThat(result).containsKey("should_renew");
            assertThat(result).containsKey("can_renew");
            assertThat(result).containsKey("within_max_lifetime");
            assertThat(result).containsKey("renewal_threshold");
        }

        @Test
        @DisplayName("예외 발생 시 에러 정보를 반환한다")
        void getTokenRenewalInfo_exception_returnsError() {
            // given
            when(jwtUtil.getRemainingTime(TEST_TOKEN)).thenThrow(new RuntimeException("Token error"));

            // when
            Map<String, Object> result = slidingExpirationService.getTokenRenewalInfo(TEST_TOKEN);

            // then
            assertThat(result).containsKey("error");
            assertThat(result.get("canRenew")).isEqualTo(false);
        }
    }
}
