package io.pinkspider.leveluptogethermvp.towerservice.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * LUT-632: 탑 인스턴스 토픽 실시간 발행기. {@code enableSimpleBroker} 는 인스턴스별 메모리 브로커라 prod(ALB 뒤 2대)에서는 {@code
 * SimpMessagingTemplate} 직접 호출이 발신자 쪽 노드 구독자에게만 간다(LUT-547) — 길드 채팅·DM·알림과 같이 Redis pub/sub 으로 발행하고
 * 각 노드의 {@link TowerRealtimeRelay} 가 자기 구독자에게 브로드캐스트한다. 활성 트랜잭션이 있으면 커밋 후 발행.
 *
 * <p>토픽: {@code /topic/tower/instances/{instanceId}}. 페이로드는 {@code @JsonNaming(SnakeCase)}
 * DTO(LUT-395). 입장·퇴장·이동 이벤트는 LUT-631 이 같은 토픽으로 보탠다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TowerRealtimePublisher {

    public static final String CHANNEL = "tower:instance:realtime";
    public static final String TOPIC_PREFIX = "/topic/tower/instances/";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public static String topicOf(Long instanceId) {
        return TOPIC_PREFIX + instanceId;
    }

    public void publishToInstance(Long instanceId, Object payload) {
        if (instanceId == null || payload == null) {
            return;
        }
        String destination = topicOf(instanceId);
        String message;
        try {
            message =
                    objectMapper.writeValueAsString(
                            Map.of("destination", destination, "body", payload));
        } catch (Exception e) {
            log.warn("탑 실시간 직렬화 실패: destination={}, error={}", destination, e.getMessage());
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            doPublish(destination, message);
                        }
                    });
        } else {
            doPublish(destination, message);
        }
    }

    private void doPublish(String destination, String message) {
        try {
            stringRedisTemplate.convertAndSend(CHANNEL, message);
            log.debug("탑 실시간 발행: destination={}", destination);
        } catch (Exception e) {
            log.warn("탑 실시간 발행 실패: destination={}, error={}", destination, e.getMessage());
        }
    }
}
