package io.pinkspider.leveluptogethermvp.userservice.oauth.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.TokenRefreshEvent;
import java.time.Duration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenMetricsService {

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    public TokenMetricsService(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    public void recordTokenRefresh(String userId, boolean refreshTokenRenewed) {
        // 토큰 갱신 카운터
        Counter.builder("token.refresh.count")
            .tag("renewed", String.valueOf(refreshTokenRenewed))
            .register(meterRegistry)
            .increment();

        // 사용자별 갱신 기록
        String key = "metrics:token_refresh:" + userId;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofDays(30));
    }

    public void recordTokenRenewal(String userId) {
        Counter.builder("token.renewal.count")
            .register(meterRegistry)
            .increment();

        String key = "metrics:token_renewal:" + userId;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofDays(30));
    }

    @EventListener
    public void handleTokenRefreshEvent(TokenRefreshEvent event) {
        recordTokenRefresh(event.getUserId(), event.isRefreshTokenRenewed());

        if (event.isRefreshTokenRenewed()) {
            recordTokenRenewal(event.getUserId());
        }
    }
}
