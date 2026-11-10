package io.pinkspider.leveluptogethermvp.towerservice.presence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LUT-705: 웹소켓 이동 메시지 빈도 제한 — 세션마다 1초에 {@code tower.presence.max-moves-per-second}(기본 5)건까지, 넘는 이동은
 * 조용히 버린다. 비인증(방문객) 연결이 열리므로 이동 폭주로 방 전체에 MOVE 브로드캐스트가 쏟아지는 것을 막는다. 한 세션은 한 노드에만 붙으므로 노드 메모리로
 * 정확하다(끊기면 지운다).
 */
@Component
public class TowerMoveRateLimiter {
    private final int maxPerSecond;
    private final LongSupplier clock;
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    @Autowired
    public TowerMoveRateLimiter(
            @Value("${tower.presence.max-moves-per-second:5}") int maxPerSecond) {
        this(maxPerSecond, System::currentTimeMillis);
    }

    TowerMoveRateLimiter(int maxPerSecond, LongSupplier clock) {
        this.maxPerSecond = maxPerSecond;
        this.clock = clock;
    }

    public boolean allow(String sessionId) {
        if (sessionId == null || maxPerSecond <= 0) {
            return true;
        }
        long second = clock.getAsLong() / 1000;
        long[] window =
                windows.compute(
                        sessionId,
                        (key, current) -> {
                            if (current == null || current[0] != second) {
                                return new long[] {second, 1};
                            }
                            current[1]++;
                            return current;
                        });
        return window[1] <= maxPerSecond;
    }

    public void forget(String sessionId) {
        if (sessionId != null) {
            windows.remove(sessionId);
        }
    }

    int tracked() {
        return windows.size();
    }
}
