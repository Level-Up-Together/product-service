package io.pinkspider.global.config;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * LUT-705: 비로그인(토큰 없는) 웹소켓 연결 수를 IP 마다 제한한다 — 탑 방문객이 인증 없이 연결을 열 수 있어서다. Redis 정렬 집합 {@code
 * ws:anon:{ip}} 에 세션 ID 를 연결 시각 점수로 넣고, 끊길 때 뺀다. 노드가 죽어 빠지지 못한 항목은 {@code stale-hours} 지나면 세지 않는다.
 * Redis 장애 시에는 통과시킨다(연결이 막히는 것보다 낫다).
 */
@Slf4j
@Component
public class WebSocketAnonymousConnectionLimiter {
    static final String KEY_PREFIX = "ws:anon:";

    private final StringRedisTemplate redis;
    private final int maxPerIp;
    private final long staleMillis;

    public WebSocketAnonymousConnectionLimiter(
            StringRedisTemplate redis,
            @Value("${app.websocket.anonymous.max-per-ip:20}") int maxPerIp,
            @Value("${app.websocket.anonymous.stale-hours:12}") int staleHours) {
        this.redis = redis;
        this.maxPerIp = maxPerIp;
        this.staleMillis = Duration.ofHours(staleHours).toMillis();
    }

    /** 이 IP 의 비로그인 연결 하나를 잡는다. 상한이면 false */
    public boolean tryAcquire(String ip, String sessionId) {
        if (ip == null || ip.isBlank() || sessionId == null || maxPerIp <= 0) {
            return true;
        }
        String key = KEY_PREFIX + ip;
        long now = System.currentTimeMillis();
        try {
            redis.opsForZSet().removeRangeByScore(key, 0, now - staleMillis);
            Long count = redis.opsForZSet().zCard(key);
            if (count != null && count >= maxPerIp) {
                log.warn("[ws] 비로그인 연결 상한: ip={} count={}", ip, count);
                return false;
            }
            redis.opsForZSet().add(key, sessionId, now);
            redis.expire(key, Duration.ofMillis(staleMillis));
            return true;
        } catch (RuntimeException e) {
            log.warn("[ws] 비로그인 연결 제한 Redis 오류 — 통과: {}", e.getMessage());
            return true;
        }
    }

    public void release(String ip, String sessionId) {
        if (ip == null || ip.isBlank() || sessionId == null) {
            return;
        }
        try {
            redis.opsForZSet().remove(KEY_PREFIX + ip, sessionId);
        } catch (RuntimeException e) {
            log.warn("[ws] 비로그인 연결 해제 실패: {}", e.getMessage());
        }
    }
}
