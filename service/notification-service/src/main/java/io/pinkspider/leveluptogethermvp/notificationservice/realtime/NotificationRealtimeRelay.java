package io.pinkspider.leveluptogethermvp.notificationservice.realtime;

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
 * 알림 실시간 릴레이 (QA-224)
 * <p>
 * Redis pub/sub 채널({@link NotificationRealtimePublisher#CHANNEL})을 구독하여,
 * 이 인스턴스에 연결된 해당 유저의 WebSocket 세션으로 STOMP 메시지를 전달한다.
 * 목적지: /user/queue/notifications (user destination — 세션 단위로 격리되어 타 유저 구독 불가)
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class NotificationRealtimeRelay implements MessageListener {

    public static final String USER_DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody());
            JsonNode root = objectMapper.readTree(payload);
            String userId = root.path("user_id").asText(null);
            if (userId == null || userId.isBlank()) {
                log.warn("실시간 알림 릴레이: user_id 누락");
                return;
            }

            // 유저가 이 인스턴스에 연결돼 있지 않으면 세션 미해석으로 조용히 무시된다 (타 인스턴스가 전달)
            messagingTemplate.convertAndSendToUser(userId, USER_DESTINATION, payload);
            log.debug("실시간 알림 릴레이: userId={}", userId);
        } catch (Exception e) {
            log.warn("실시간 알림 릴레이 실패: {}", e.getMessage());
        }
    }
}
