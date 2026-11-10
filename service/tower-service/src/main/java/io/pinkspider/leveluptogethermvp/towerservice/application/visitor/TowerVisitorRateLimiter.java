package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import java.time.Duration;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * LUT-705: 방문객의 IP 단위 빈도 제한(Redis, prod 2대 공유). 비인증으로 열리므로 점유자·세션 단위 제한(5초 간격)만으로는 키를 바꿔 우회할 수 있다.
 * IP 가 없으면(내부 호출) 제한하지 않는다. Redis 장애 시 통과시킨다.
 */
@Slf4j
@Component
public class TowerVisitorRateLimiter {
    static final String ENTER_PREFIX = "tower:visitor:enter:";
    static final String SESSIONS_PREFIX = "tower:visitor:ip:";
    static final String CHAT_PREFIX = "tower:visitor:chat:";

    private final StringRedisTemplate redis;
    private final TowerVisitorProperties properties;
    private final LongSupplier clock;

    @Autowired
    public TowerVisitorRateLimiter(StringRedisTemplate redis, TowerVisitorProperties properties) {
        this(redis, properties, System::currentTimeMillis);
    }

    TowerVisitorRateLimiter(
            StringRedisTemplate redis, TowerVisitorProperties properties, LongSupplier clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 방문객 입장 — 1분 입장 수와 그 IP 의 쓰는 중인 세션 수를 본다. 이미 쓰던 세션(같은 키)의 재입장은 세션 수를 늘리지 않는다.
     *
     * @return 통과하면 true
     */
    public boolean allowEnter(@Nullable String ip, String visitorKey) {
        if (ip == null) {
            return true;
        }
        long now = clock.getAsLong();
        try {
            if (!withinPerMinute(ENTER_PREFIX + ip, now, properties.getEnterPerMinutePerIp())) {
                log.warn("[tower-visitor] 입장 빈도 초과: ip={}", ip);
                return false;
            }
            String key = SESSIONS_PREFIX + ip;
            long window = Duration.ofMinutes(properties.getSessionWindowMinutes()).toMillis();
            redis.opsForZSet().removeRangeByScore(key, 0, now - window);
            Double known = redis.opsForZSet().score(key, visitorKey);
            if (known == null) {
                Long count = redis.opsForZSet().zCard(key);
                if (count != null && count >= properties.getSessionsPerIp()) {
                    log.warn("[tower-visitor] IP 세션 수 초과: ip={} count={}", ip, count);
                    return false;
                }
            }
            redis.opsForZSet().add(key, visitorKey, now);
            redis.expire(key, Duration.ofMillis(window));
            return true;
        } catch (RuntimeException e) {
            log.warn("[tower-visitor] 입장 제한 Redis 오류 — 통과: {}", e.getMessage());
            return true;
        }
    }

    /** 방문객 채팅·리액션 — 같은 IP 의 1분 전송 수 */
    public boolean allowChat(@Nullable String ip) {
        if (ip == null) {
            return true;
        }
        try {
            return withinPerMinute(
                    CHAT_PREFIX + ip, clock.getAsLong(), properties.getChatPerMinutePerIp());
        } catch (RuntimeException e) {
            log.warn("[tower-visitor] 채팅 제한 Redis 오류 — 통과: {}", e.getMessage());
            return true;
        }
    }

    private boolean withinPerMinute(String prefix, long now, int limit) {
        String key = prefix + ":" + (now / 60_000);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofMinutes(2));
        }
        return count == null || count <= limit;
    }
}
