package io.pinkspider.leveluptogethermvp.towerservice.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * LUT-632: Redis 채널({@link TowerRealtimePublisher#CHANNEL})을 받아 이 노드에 연결된 탑 인스턴스 토픽 구독자에게 STOMP 로
 * 브로드캐스트한다. body 는 직렬화된 JSON 문자열 그대로(프론트는 {@code JSON.parse}). 구독자가 없는 노드는 SimpleBroker 가 조용히 무시한다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class TowerRealtimeRelay implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode root = objectMapper.readTree(new String(message.getBody()));
            String destination = root.path("destination").asText(null);
            JsonNode body = root.path("body");
            if (destination == null
                    || !destination.startsWith(TowerRealtimePublisher.TOPIC_PREFIX)
                    || body.isMissingNode()) {
                log.warn("탑 실시간 릴레이: 필수 필드 누락 또는 탑 토픽 아님 destination={}", destination);
                return;
            }
            messagingTemplate.convertAndSend(destination, body.toString());
            log.debug("탑 실시간 릴레이: destination={}", destination);
        } catch (Exception e) {
            log.warn("탑 실시간 릴레이 실패: {}", e.getMessage());
        }
    }
}
