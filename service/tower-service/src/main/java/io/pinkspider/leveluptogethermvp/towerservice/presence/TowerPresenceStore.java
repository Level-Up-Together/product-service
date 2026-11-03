package io.pinkspider.leveluptogethermvp.towerservice.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * LUT-631: presence 공유 저장소(Redis). 노드 메모리에 두지 않는다 — prod 는 ALB 뒤 2대라 같은 인스턴스 유저가 다른 노드에 붙을 수 있다.
 *
 * <ul>
 *   <li>{@code tower:presence:{instanceId}} — 해시(점유자 ID → {@link TowerPresence} JSON)
 *   <li>{@code tower:ws:session:{sessionId}} — STOMP 세션 → "instanceId:occupantId" (끊김 감지), TTL 1일
 *   <li>{@code tower:ws:occupant:{occupantId}} — 점유자 → 세션(재접속 시 옛 세션 정리), TTL 1일
 * </ul>
 *
 * Redis 장애는 로그만 남기고 빈 결과(fail-open) — 입장·퇴장 자체(DB)는 막지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerPresenceStore {

    static final String PRESENCE_PREFIX = "tower:presence:";
    static final String SESSION_PREFIX = "tower:ws:session:";
    static final String OCCUPANT_SESSION_PREFIX = "tower:ws:occupant:";
    static final Duration SESSION_TTL = Duration.ofDays(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public void save(TowerPresence presence) {
        try {
            redis.opsForHash()
                    .put(
                            PRESENCE_PREFIX + presence.instanceId(),
                            String.valueOf(presence.occupantId()),
                            objectMapper.writeValueAsString(presence));
        } catch (Exception e) {
            log.warn(
                    "[tower] presence 저장 실패: occupantId={} error={}",
                    presence.occupantId(),
                    e.getMessage());
        }
    }

    public Optional<TowerPresence> find(Long instanceId, Long occupantId) {
        try {
            Object raw =
                    redis.opsForHash()
                            .get(PRESENCE_PREFIX + instanceId, String.valueOf(occupantId));
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw.toString(), TowerPresence.class));
        } catch (Exception e) {
            log.warn("[tower] presence 조회 실패: occupantId={} error={}", occupantId, e.getMessage());
            return Optional.empty();
        }
    }

    public void delete(Long instanceId, Long occupantId) {
        try {
            redis.opsForHash().delete(PRESENCE_PREFIX + instanceId, String.valueOf(occupantId));
        } catch (Exception e) {
            log.warn("[tower] presence 삭제 실패: occupantId={} error={}", occupantId, e.getMessage());
        }
    }

    public List<TowerPresence> listByInstance(Long instanceId) {
        List<TowerPresence> result = new ArrayList<>();
        try {
            Map<Object, Object> entries = redis.opsForHash().entries(PRESENCE_PREFIX + instanceId);
            for (Object raw : entries.values()) {
                try {
                    result.add(objectMapper.readValue(raw.toString(), TowerPresence.class));
                } catch (Exception e) {
                    log.warn(
                            "[tower] presence 역직렬화 실패: instanceId={} error={}",
                            instanceId,
                            e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn(
                    "[tower] presence 목록 조회 실패: instanceId={} error={}",
                    instanceId,
                    e.getMessage());
        }
        return result;
    }

    public void bindSession(String sessionId, TowerSessionBinding binding) {
        try {
            redis.opsForValue().set(SESSION_PREFIX + sessionId, binding.encode(), SESSION_TTL);
            redis.opsForValue()
                    .set(OCCUPANT_SESSION_PREFIX + binding.occupantId(), sessionId, SESSION_TTL);
        } catch (Exception e) {
            log.warn("[tower] 세션 묶기 실패: sessionId={} error={}", sessionId, e.getMessage());
        }
    }

    /** 세션을 풀고 묶여 있던 점유자를 돌려준다. 점유자의 현재 세션이 이 세션일 때만 점유자 쪽 키도 지운다(재접속 뒤 옛 세션 끊김 보호) */
    public Optional<TowerSessionBinding> unbindSession(String sessionId) {
        try {
            String raw = redis.opsForValue().get(SESSION_PREFIX + sessionId);
            redis.delete(SESSION_PREFIX + sessionId);
            TowerSessionBinding binding = TowerSessionBinding.decode(raw);
            if (binding == null) {
                return Optional.empty();
            }
            String current =
                    redis.opsForValue().get(OCCUPANT_SESSION_PREFIX + binding.occupantId());
            if (sessionId.equals(current)) {
                redis.delete(OCCUPANT_SESSION_PREFIX + binding.occupantId());
                return Optional.of(binding);
            }
            return Optional.empty(); // 이미 새 세션으로 갈아탔다 — 옛 세션 끊김은 무시
        } catch (Exception e) {
            log.warn("[tower] 세션 풀기 실패: sessionId={} error={}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<TowerSessionBinding> findBinding(String sessionId) {
        try {
            return Optional.ofNullable(
                    TowerSessionBinding.decode(
                            redis.opsForValue().get(SESSION_PREFIX + sessionId)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<String> findSessionOfOccupant(Long occupantId) {
        try {
            return Optional.ofNullable(
                    redis.opsForValue().get(OCCUPANT_SESSION_PREFIX + occupantId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void unbindOccupant(Long occupantId) {
        try {
            String session = redis.opsForValue().get(OCCUPANT_SESSION_PREFIX + occupantId);
            if (session != null) {
                redis.delete(SESSION_PREFIX + session);
            }
            redis.delete(OCCUPANT_SESSION_PREFIX + occupantId);
        } catch (Exception e) {
            log.warn("[tower] 점유자 세션 정리 실패: occupantId={} error={}", occupantId, e.getMessage());
        }
    }
}
