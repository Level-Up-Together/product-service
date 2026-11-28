package io.pinkspider.leveluptogethermvp.towerservice.presence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LUT-869: 웹소켓 점프 메시지 최소 간격 — 세션마다 직전에 받아들인 점프로부터 {@code tower.presence.jump-min-interval-ms}(기본
 * 400ms) 안의 요청은 조용히 버린다. 화면의 점프 길이는 450ms 라 정상 클라이언트는 그보다 자주 보내지 않는다 — 기본값을 조금 짧게 둔 것은 네트워크 지연이
 * 요청마다 달라 이어 뛴 두 번째 점프가 서버에는 450ms 안에 도착할 수 있어서다. 세션은 join 으로 점유자 하나에 묶이므로 세션 간격이 곧 점유자 간격이고, 한 세션은
 * 한 노드에만 붙어 노드 메모리로 정확하다(끊기면 지운다).
 */
@Component
public class TowerJumpRateLimiter {
    private final long minIntervalMs;
    private final LongSupplier clock;
    private final Map<String, Long> lastAllowedAt = new ConcurrentHashMap<>();

    @Autowired
    public TowerJumpRateLimiter(
            @Value("${tower.presence.jump-min-interval-ms:400}") long minIntervalMs) {
        this(minIntervalMs, System::currentTimeMillis);
    }

    TowerJumpRateLimiter(long minIntervalMs, LongSupplier clock) {
        this.minIntervalMs = minIntervalMs;
        this.clock = clock;
    }

    public boolean allow(String sessionId) {
        if (sessionId == null || minIntervalMs <= 0) {
            return true;
        }
        long now = clock.getAsLong();
        boolean[] allowed = {false};
        // 받아들인 점프만 기준 시각이 된다 — 버린 요청이 간격을 늘리지 않는다
        lastAllowedAt.compute(
                sessionId,
                (key, last) -> {
                    if (last == null || now - last >= minIntervalMs) {
                        allowed[0] = true;
                        return now;
                    }
                    return last;
                });
        return allowed[0];
    }

    public void forget(String sessionId) {
        if (sessionId != null) {
            lastAllowedAt.remove(sessionId);
        }
    }

    int tracked() {
        return lastAllowedAt.size();
    }
}
