package io.pinkspider.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.annotation.CurrentUser;
import java.lang.reflect.Method;
import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class PerUserRateLimitAspectTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private PerUserRateLimit perUserRateLimit;

    @InjectMocks
    private PerUserRateLimitAspect aspect;

    @Nested
    @DisplayName("rateLimit 테스트")
    class RateLimitTest {

        @Test
        @DisplayName("userId를 찾을 수 없으면 rate limit을 적용하지 않는다")
        void skipsWhenNoUserId() throws Throwable {
            // given
            Method method = TestController.class.getMethod("noUserIdMethod", String.class);
            when(joinPoint.getSignature()).thenReturn(methodSignature);
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"not-a-userId"});
            when(joinPoint.proceed()).thenReturn("ok");

            // when
            Object result = aspect.rateLimit(joinPoint, perUserRateLimit);

            // then
            assertThat(result).isEqualTo("ok");
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("첫 번째 요청이면 TTL을 설정하고 통과한다")
        void firstRequestSetsTtlAndProceeds() throws Throwable {
            // given
            Method method = TestController.class.getMethod("withUserId", String.class);
            when(joinPoint.getSignature()).thenReturn(methodSignature);
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"user-1"});
            when(perUserRateLimit.name()).thenReturn("test-api");
            when(perUserRateLimit.limit()).thenReturn(10);
            when(perUserRateLimit.windowSeconds()).thenReturn(60);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("ratelimit:test-api:user-1")).thenReturn(1L);
            when(joinPoint.proceed()).thenReturn("ok");

            // when
            Object result = aspect.rateLimit(joinPoint, perUserRateLimit);

            // then
            assertThat(result).isEqualTo("ok");
            verify(redisTemplate).expire(eq("ratelimit:test-api:user-1"), any(Duration.class));
        }

        @Test
        @DisplayName("rate limit 초과 시 ResponseEntity 타입이면 429를 반환한다")
        void returnsRateLimitResponseForResponseEntity() throws Throwable {
            // given
            Method method = TestController.class.getMethod("withUserId", String.class);
            when(joinPoint.getSignature()).thenReturn(methodSignature);
            when(methodSignature.getMethod()).thenReturn(method);
            when(methodSignature.getReturnType()).thenReturn((Class) ResponseEntity.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"user-1"});
            when(perUserRateLimit.name()).thenReturn("test-api");
            when(perUserRateLimit.limit()).thenReturn(5);
            when(perUserRateLimit.windowSeconds()).thenReturn(60);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("ratelimit:test-api:user-1")).thenReturn(6L);

            // when
            Object result = aspect.rateLimit(joinPoint, perUserRateLimit);

            // then
            assertThat(result).isInstanceOf(ResponseEntity.class);
            ResponseEntity<?> response = (ResponseEntity<?>) result;
            assertThat(response.getStatusCode().value()).isEqualTo(429);
        }

        @Test
        @DisplayName("rate limit 초과 시 기타 반환 타입이면 예외를 발생시킨다")
        void throwsExceptionForNonResponseEntityType() throws Throwable {
            // given
            Method method = TestController.class.getMethod("withUserId", String.class);
            when(joinPoint.getSignature()).thenReturn(methodSignature);
            when(methodSignature.getMethod()).thenReturn(method);
            when(methodSignature.getReturnType()).thenReturn((Class) String.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"user-1"});
            when(perUserRateLimit.name()).thenReturn("test-api");
            when(perUserRateLimit.limit()).thenReturn(5);
            when(perUserRateLimit.windowSeconds()).thenReturn(60);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("ratelimit:test-api:user-1")).thenReturn(6L);

            // when & then
            assertThatThrownBy(() -> aspect.rateLimit(joinPoint, perUserRateLimit))
                .isInstanceOf(PerUserRateLimitAspect.RateLimitExceededException.class);
        }

        @Test
        @DisplayName("Redis increment가 null을 반환하면 rate limit을 적용하지 않는다")
        void skipsWhenRedisReturnsNull() throws Throwable {
            // given
            Method method = TestController.class.getMethod("withUserId", String.class);
            when(joinPoint.getSignature()).thenReturn(methodSignature);
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"user-1"});
            when(perUserRateLimit.name()).thenReturn("test");
            when(perUserRateLimit.windowSeconds()).thenReturn(60);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(null);
            when(joinPoint.proceed()).thenReturn("ok");

            // when
            Object result = aspect.rateLimit(joinPoint, perUserRateLimit);

            // then
            assertThat(result).isEqualTo("ok");
        }
    }

    // Test helper class
    public static class TestController {
        public String noUserIdMethod(String data) { return "ok"; }
        public ResponseEntity<String> withUserId(String userId) { return ResponseEntity.ok("ok"); }
        public String withAnnotatedUser(@CurrentUser String userId) { return "ok"; }
    }
}
