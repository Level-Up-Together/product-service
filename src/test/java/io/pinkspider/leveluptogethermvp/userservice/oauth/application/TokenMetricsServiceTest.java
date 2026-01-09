package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.TokenRefreshEvent;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenMetricsServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MeterRegistry meterRegistry;
    private TokenMetricsService tokenMetricsService;

    private static final String TEST_USER_ID = "test-user-123";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        tokenMetricsService = new TokenMetricsService(redisTemplate, meterRegistry);
    }

    @Nested
    @DisplayName("recordTokenRefresh 테스트")
    class RecordTokenRefreshTest {

        @Test
        @DisplayName("토큰 갱신을 기록한다")
        void recordTokenRefresh_success() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(1L);

            // when
            tokenMetricsService.recordTokenRefresh(TEST_USER_ID, false);

            // then
            verify(valueOperations).increment("metrics:token_refresh:" + TEST_USER_ID);
            verify(redisTemplate).expire(eq("metrics:token_refresh:" + TEST_USER_ID), eq(Duration.ofDays(30)));
        }

        @Test
        @DisplayName("리프레시 토큰 갱신 시 기록한다")
        void recordTokenRefresh_withRenewal() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(1L);

            // when
            tokenMetricsService.recordTokenRefresh(TEST_USER_ID, true);

            // then
            verify(valueOperations).increment("metrics:token_refresh:" + TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("recordTokenRenewal 테스트")
    class RecordTokenRenewalTest {

        @Test
        @DisplayName("토큰 갱신 기록을 저장한다")
        void recordTokenRenewal_success() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(1L);

            // when
            tokenMetricsService.recordTokenRenewal(TEST_USER_ID);

            // then
            verify(valueOperations).increment("metrics:token_renewal:" + TEST_USER_ID);
            verify(redisTemplate).expire(eq("metrics:token_renewal:" + TEST_USER_ID), eq(Duration.ofDays(30)));
        }
    }

    @Nested
    @DisplayName("handleTokenRefreshEvent 테스트")
    class HandleTokenRefreshEventTest {

        @Test
        @DisplayName("토큰 갱신 이벤트를 처리한다")
        void handleTokenRefreshEvent_success() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(1L);

            TokenRefreshEvent event = new TokenRefreshEvent(TEST_USER_ID, "mobile", "device-123", false, 1000L);

            // when
            tokenMetricsService.handleTokenRefreshEvent(event);

            // then
            verify(valueOperations).increment("metrics:token_refresh:" + TEST_USER_ID);
        }

        @Test
        @DisplayName("리프레시 토큰 갱신 이벤트 시 갱신 기록도 저장한다")
        void handleTokenRefreshEvent_withRenewal() {
            // given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(1L);

            TokenRefreshEvent event = new TokenRefreshEvent(TEST_USER_ID, "mobile", "device-123", true, 1000L);

            // when
            tokenMetricsService.handleTokenRefreshEvent(event);

            // then
            verify(valueOperations).increment("metrics:token_refresh:" + TEST_USER_ID);
            verify(valueOperations).increment("metrics:token_renewal:" + TEST_USER_ID);
        }
    }
}
