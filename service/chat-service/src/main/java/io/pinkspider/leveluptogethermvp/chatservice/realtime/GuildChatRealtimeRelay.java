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
 * 길드 단체채팅 토픽 실시간 릴레이 (LUT-549)
 *
 * <p>Redis pub/sub 채널({@link GuildChatRealtimePublisher#CHANNEL})을 구독하여, 이 인스턴스에 연결된 토픽 구독자에게 STOMP
 * 메시지를 브로드캐스트한다. 토픽 구독자가 이 인스턴스에 없으면 SimpleBroker가 조용히 무시한다 (구독자가 있는 다른 인스턴스가 전달).
 *
 * <p>body는 이미 직렬화된 JSON 문자열로 전달한다 — 프론트(chat-websocket.ts)는 content-type과 무관하게 {@code
 * JSON.parse(body)}로 읽는다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class GuildChatRealtimeRelay implements MessageListener {

    private static final String TOPIC_PREFIX = "/topic/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody());
            JsonNode root = objectMapper.readTree(payload);
            String destination = root.path("destination").asText(null);
            JsonNode body = root.path("body");
            if (destination == null
                    || !destination.startsWith(TOPIC_PREFIX)
                    || body.isMissingNode()) {
                log.warn("길드 채팅 실시간 릴레이: 필수 필드 누락 또는 토픽 아님 destination={}", destination);
                return;
            }

            messagingTemplate.convertAndSend(destination, body.toString());
            log.debug("길드 채팅 실시간 릴레이: destination={}", destination);
        } catch (Exception e) {
            log.warn("길드 채팅 실시간 릴레이 실패: {}", e.getMessage());
        }
    }
}
