package io.pinkspider.leveluptogethermvp.chatservice.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 길드 단체채팅 토픽 실시간 발행기 (LUT-549)
 *
 * <p>{@code enableSimpleBroker}는 인스턴스별 메모리 브로커라 {@code /topic/guild/{guildId}/chat} 같은 토픽 브로드캐스트도
 * 발신자가 붙은 인스턴스의 구독자에게만 전달된다. prod는 ALB 뒤 2대(스티키 세션 없음)라 B가 보낸 메시지가 다른 인스턴스에 연결된 A에게는 도착하지 않았다
 * (LUT-547 — "내 메시지만 보이고 상대 메시지는 재진입 후 노출"). DM(LUT-263)·알림(QA-224)과 동일하게 Redis pub/sub으로 발행하고, 각
 * 인스턴스의 {@link GuildChatRealtimeRelay}가 자신에게 연결된 구독자에게 브로드캐스트한다.
 *
 * <p>활성 트랜잭션이 있으면 커밋 후(afterCommit) 발행 — 수신 클라이언트가 재조회 시 미커밋 데이터를 놓치는 것을 방지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuildChatRealtimePublisher {

    public static final String CHANNEL = "chat:guild:realtime";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * STOMP 토픽 destination으로 payload를 모든 인스턴스에 브로드캐스트한다.
     *
     * @param destination 토픽 destination (예: "/topic/guild/1/chat", "/topic/guild/1/read")
     * @param payload 직렬화 가능한 DTO
     */
    public void publishToTopic(String destination, Object payload) {
        if (destination == null || destination.isBlank() || payload == null) {
            return;
        }

        String message;
        try {
            message =
                    objectMapper.writeValueAsString(
                            Map.of("destination", destination, "body", payload));
        } catch (Exception e) {
            log.warn("길드 채팅 실시간 직렬화 실패: destination={}, error={}", destination, e.getMessage());
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
            log.debug("길드 채팅 실시간 발행: destination={}", destination);
        } catch (Exception e) {
            log.warn("길드 채팅 실시간 발행 실패: destination={}, error={}", destination, e.getMessage());
        }
    }
}
