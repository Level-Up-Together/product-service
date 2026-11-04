package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerInstanceEvent;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * LUT-632/633: 완료 연출 뒤 응원 창(10초). {@code MISSION_COMPLETED} 를 보낼 때 열고, 그 안에 온 리액션만 응원으로 센다. 한 사람이
 * 같은 완료에 여러 번 보내도 응원은 1회.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TowerCheerWindowStore {
    static final String WINDOW_PREFIX = "tower:cheer:window:";
    static final String CHEERED_PREFIX = "tower:cheer:done:";

    private final StringRedisTemplate redis;

    public void open(Long instanceId, String userId) {
        try {
            redis.opsForValue()
                    .set(
                            WINDOW_PREFIX + instanceId + ":" + userId,
                            "1",
                            Duration.ofSeconds(TowerInstanceEvent.CHEER_WINDOW_SECONDS));
        } catch (RuntimeException e) {
            log.warn("[tower-chat] 응원 창 열기 실패: {}", e.getMessage());
        }
    }

    public boolean isOpen(Long instanceId, String userId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(WINDOW_PREFIX + instanceId + ":" + userId));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 이 점유자가 이 완료에 처음 보내는 응원이면 true */
    public boolean markCheered(Long instanceId, String targetUserId, Long occupantId) {
        try {
            Boolean first =
                    redis.opsForValue()
                            .setIfAbsent(
                                    CHEERED_PREFIX
                                            + instanceId
                                            + ":"
                                            + targetUserId
                                            + ":"
                                            + occupantId,
                                    "1",
                                    Duration.ofSeconds(
                                            TowerInstanceEvent.CHEER_WINDOW_SECONDS * 6L));
            return Boolean.TRUE.equals(first);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
