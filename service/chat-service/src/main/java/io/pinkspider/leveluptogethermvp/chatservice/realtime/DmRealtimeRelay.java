package io.pinkspider.leveluptogethermvp.chatservice.realtime;

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
 * DM 실시간 릴레이 (LUT-263)
 *
 * <p>Redis pub/sub 채널({@link DmRealtimePublisher#CHANNEL})을 구독하여, 이 인스턴스에 연결된 해당 유저의
 * WebSocket 세션으로 STOMP 메시지를 전달한다. 유저가 이 인스턴스에 연결돼 있지 않으면 세션 미해석으로 조용히
 * 무시된다 (해당 유저가 연결된 다른 인스턴스가 전달).
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DmRealtimeRelay implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody());
            JsonNode root = objectMapper.readTree(payload);
            String userId = root.path("user_id").asText(null);
            String destination = root.path("destination").asText(null);
            JsonNode body = root.path("body");
            if (userId == null || userId.isBlank() || destination == null || body.isMissingNode()) {
                log.warn("DM 실시간 릴레이: 필수 필드 누락");
                return;
            }

            messagingTemplate.convertAndSendToUser(userId, destination, body.toString());
            log.debug("DM 실시간 릴레이: userId={}, destination={}", userId, destination);
        } catch (Exception e) {
            log.warn("DM 실시간 릴레이 실패: {}", e.getMessage());
        }
    }
}
