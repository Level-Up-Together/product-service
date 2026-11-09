package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerChatMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** LUT-633: 인스턴스별 최근 채팅(처리된 문장만) — Redis 리스트, 최대 N건·TTL. 방이 닫히면 자연 만료. */
@Component
@Slf4j
@RequiredArgsConstructor
public class TowerChatHistoryStore {
    static final String PREFIX = "tower:chat:history:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TowerChatProperties properties;

    public void push(TowerChatMessage message) {
        String key = PREFIX + message.instanceId();
        try {
            redis.opsForList().leftPush(key, objectMapper.writeValueAsString(message));
            redis.opsForList().trim(key, 0, properties.getHistorySize() - 1L);
            redis.expire(key, Duration.ofHours(properties.getHistoryTtlHours()));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn(
                    "[tower-chat] 이력 저장 실패: instanceId={} {}",
                    message.instanceId(),
                    e.getMessage());
        }
    }

    /** 오래된 것부터 */
    public List<TowerChatMessage> recent(Long instanceId) {
        try {
            List<String> raw =
                    redis.opsForList()
                            .range(PREFIX + instanceId, 0, properties.getHistorySize() - 1L);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<TowerChatMessage> messages = new ArrayList<>(raw.size());
            for (String json : raw) {
                try {
                    messages.add(objectMapper.readValue(json, TowerChatMessage.class));
                } catch (JsonProcessingException e) {
                    log.warn("[tower-chat] 이력 파싱 실패: {}", e.getMessage());
                }
            }
            Collections.reverse(messages);
            return messages;
        } catch (RuntimeException e) {
            log.warn("[tower-chat] 이력 조회 실패: instanceId={} {}", instanceId, e.getMessage());
            return List.of();
        }
    }
}
