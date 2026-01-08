package io.pinkspider.global.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rate Limiter 설정
 * API 어뷰징 방지를 위한 요청 제한 설정
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 출석 체크 Rate Limiter
     * 전역 1분에 최대 30회 요청 허용
     * TODO: 사용자별 Rate Limiter로 변경 필요 (현재 Resilience4j는 전역 적용)
     * (중복 출석은 DB에서 방지되지만, 과도한 API 호출 방지)
     */
    @Bean
    public RateLimiter attendanceRateLimiter(RateLimiterRegistry rateLimiterRegistry) {
        io.github.resilience4j.ratelimiter.RateLimiterConfig config =
            io.github.resilience4j.ratelimiter.RateLimiterConfig.custom()
                .limitForPeriod(30)                      // 1분에 30회 (전역)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(1))
                .build();

        return rateLimiterRegistry.rateLimiter("attendance", config);
    }

    /**
     * 미션 완료 Rate Limiter
     * 전역 1분에 최대 100회 요청 허용
     * TODO: 사용자별 Rate Limiter로 변경 필요 (현재 Resilience4j는 전역 적용)
     */
    @Bean
    public RateLimiter missionCompletionRateLimiter(RateLimiterRegistry rateLimiterRegistry) {
        io.github.resilience4j.ratelimiter.RateLimiterConfig config =
            io.github.resilience4j.ratelimiter.RateLimiterConfig.custom()
                .limitForPeriod(100)                     // 1분에 100회 (전역)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(1))
                .build();

        return rateLimiterRegistry.rateLimiter("missionCompletion", config);
    }

    /**
     * 길드 생성 Rate Limiter
     * 사용자당 1시간에 최대 3회 요청 허용
     */
    @Bean
    public RateLimiter guildCreationRateLimiter(RateLimiterRegistry rateLimiterRegistry) {
        io.github.resilience4j.ratelimiter.RateLimiterConfig config =
            io.github.resilience4j.ratelimiter.RateLimiterConfig.custom()
                .limitForPeriod(3)                       // 1시간에 3회
                .limitRefreshPeriod(Duration.ofHours(1))
                .timeoutDuration(Duration.ofSeconds(1))
                .build();

        return rateLimiterRegistry.rateLimiter("guildCreation", config);
    }

    /**
     * 일반 API Rate Limiter
     * 사용자당 1초에 최대 20회 요청 허용
     */
    @Bean
    public RateLimiter generalApiRateLimiter(RateLimiterRegistry rateLimiterRegistry) {
        io.github.resilience4j.ratelimiter.RateLimiterConfig config =
            io.github.resilience4j.ratelimiter.RateLimiterConfig.custom()
                .limitForPeriod(20)                      // 1초에 20회
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(500))
                .build();

        return rateLimiterRegistry.rateLimiter("generalApi", config);
    }
}
