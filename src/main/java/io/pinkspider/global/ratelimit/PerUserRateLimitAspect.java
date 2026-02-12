package io.pinkspider.global.ratelimit;

import io.pinkspider.global.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;

/**
 * 사용자별 Rate Limit Aspect
 * Redis를 사용한 Sliding Window Counter 방식
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PerUserRateLimitAspect {

    private final StringRedisTemplate redisTemplate;
    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";

    @Around("@annotation(perUserRateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, PerUserRateLimit perUserRateLimit) throws Throwable {
        String userId = extractUserId(joinPoint);

        if (userId == null) {
            // userId를 찾을 수 없으면 rate limit 적용하지 않음
            log.warn("Rate limit: userId를 찾을 수 없어 rate limit을 건너뜁니다.");
            return joinPoint.proceed();
        }

        String key = buildKey(perUserRateLimit.name(), userId);
        int limit = perUserRateLimit.limit();
        int windowSeconds = perUserRateLimit.windowSeconds();

        // 현재 요청 횟수 조회 및 증가
        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount == null) {
            // Redis 연결 실패 시 rate limit 적용하지 않음
            log.warn("Rate limit: Redis 연결 실패, rate limit을 건너뜁니다.");
            return joinPoint.proceed();
        }

        // 첫 번째 요청이면 TTL 설정
        if (currentCount == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        // Rate limit 초과 체크
        if (currentCount > limit) {
            log.warn("Rate limit 초과: userId={}, name={}, count={}/{}, window={}s",
                userId, perUserRateLimit.name(), currentCount, limit, windowSeconds);

            return createRateLimitExceededResponse(joinPoint);
        }

        log.debug("Rate limit 체크 통과: userId={}, name={}, count={}/{}",
            userId, perUserRateLimit.name(), currentCount, limit);

        return joinPoint.proceed();
    }

    /**
     * 메소드 파라미터에서 userId 추출
     * @CurrentUser 어노테이션이 붙은 String 파라미터를 찾음
     */
    private String extractUserId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            // @CurrentUser 어노테이션 확인
            if (parameters[i].isAnnotationPresent(
                    io.pinkspider.global.annotation.CurrentUser.class)) {
                if (args[i] instanceof String) {
                    return (String) args[i];
                }
            }
            // 파라미터 이름이 userId인 경우
            if ("userId".equals(parameters[i].getName()) && args[i] instanceof String) {
                return (String) args[i];
            }
        }

        return null;
    }

    private String buildKey(String name, String userId) {
        return RATE_LIMIT_KEY_PREFIX + name + ":" + userId;
    }

    /**
     * Rate Limit 초과 응답 생성
     */
    @SuppressWarnings("unchecked")
    private Object createRateLimitExceededResponse(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getReturnType();

        // ResponseEntity<ApiResult<T>> 타입인 경우
        if (ResponseEntity.class.isAssignableFrom(returnType)) {
            return ResponseEntity.status(429)
                .body(ApiResult.builder()
                    .code("TOO_MANY_REQUESTS")
                    .message("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.")
                    .build());
        }

        // 그 외의 경우 예외 발생
        throw new RateLimitExceededException("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
    }

    /**
     * Rate Limit 초과 예외
     */
    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
