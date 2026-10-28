package io.pinkspider.leveluptogethermvp.statsservice.activity.application;

import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.UserActivityKeys;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * LUT-598: Redis {@code active:{KST날짜}} / {@code active:{KST날짜}:{deviceType}} 집합에 userId 를 넣는다.
 *
 * <p>요청 스레드를 붙잡지 않도록 비동기({@code taskExecutor})로 돈다. Redis 장애 시 그 구간의 DAU 는 결측으로 남고(허용), 예외는 로그로만
 * 남는다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserActivityRecorder {

    private final StringRedisTemplate redisTemplate;

    @Async
    public void recordAsync(String userId, String deviceType) {
        record(userId, deviceType, LocalDate.now(UserActivityKeys.KST));
    }

    void record(String userId, String deviceType, LocalDate date) {
        try {
            addWithTtl(UserActivityKeys.dailyKey(date), userId);
            if (deviceType != null && !deviceType.isBlank()) {
                addWithTtl(UserActivityKeys.deviceKey(date, deviceType), userId);
            }
        } catch (Exception e) {
            log.warn("활동 기록 실패(무시): userId={}, date={}, cause={}", userId, date, e.toString());
        }
    }

    /** 새 멤버가 들어갔을 때만 TTL 을 건다 — 키마다 첫 적재 시 1회면 충분하고, 재설정도 무해하다 */
    private void addWithTtl(String key, String userId) {
        Long added = redisTemplate.opsForSet().add(key, userId);
        if (added != null && added > 0) {
            redisTemplate.expire(key, UserActivityKeys.TTL);
        }
    }
}
