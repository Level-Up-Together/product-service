package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** LUT-705: 방문객 세션 저장소(Redis {@code tower:visitor:session:{key}}, 노드 공유). 읽기 실패는 "없음"(새 세션) */
@Slf4j
@Component
@RequiredArgsConstructor
public class TowerVisitorSessionStore {
    static final String KEY_PREFIX = "tower:visitor:session:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TowerVisitorProperties properties;

    public Optional<TowerVisitorSession> find(String visitorKey) {
        if (visitorKey == null || visitorKey.isBlank()) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + visitorKey);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, TowerVisitorSession.class));
        } catch (Exception e) {
            log.warn("[tower-visitor] 세션 읽기 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void save(TowerVisitorSession session) {
        try {
            redis.opsForValue()
                    .set(
                            KEY_PREFIX + session.visitorKey(),
                            objectMapper.writeValueAsString(session),
                            Duration.ofHours(properties.getSessionTtlHours()));
        } catch (Exception e) {
            log.warn("[tower-visitor] 세션 저장 실패: {}", e.getMessage());
        }
    }
}
