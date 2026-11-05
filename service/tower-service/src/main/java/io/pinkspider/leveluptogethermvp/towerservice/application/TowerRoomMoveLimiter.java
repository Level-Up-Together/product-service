package io.pinkspider.leveluptogethermvp.towerservice.application;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * LUT-738: 방 이동은 10초에 한 번 — 단위는 유저 {@code u:{userId}} / 방문객 세션 {@code v:{key}}(채팅 제한과 같다). 이동에 성공했을
 * 때만 표시한다(거절된 시도는 세지 않는다). Redis 장애 시 제한은 통과(fail-open).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerRoomMoveLimiter {

    static final String PREFIX = "tower:room-move:";
    public static final int COOLDOWN_SECONDS = 10;

    private final StringRedisTemplate redis;

    /** 남은 대기 초 (없으면 0) */
    public long remainingSeconds(String subject) {
        try {
            Long ttl = redis.getExpire(PREFIX + subject);
            return ttl == null || ttl < 0 ? 0 : Math.max(1, ttl);
        } catch (RuntimeException e) {
            log.warn("[tower] 방 이동 제한 조회 실패 — 통과: {}", e.getMessage());
            return 0;
        }
    }

    public void markMoved(String subject) {
        try {
            redis.opsForValue().set(PREFIX + subject, "1", Duration.ofSeconds(COOLDOWN_SECONDS));
        } catch (RuntimeException e) {
            log.warn("[tower] 방 이동 제한 기록 실패: {}", e.getMessage());
        }
    }
}
