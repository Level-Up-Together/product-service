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
 * DM 실시간 발행기 (LUT-263)
 *
 * <p>SimpleBroker는 인스턴스별 메모리 브로커라 멀티 인스턴스 환경에서 수신자의 WebSocket 세션이 다른
 * 인스턴스에 있으면 {@code convertAndSendToUser}가 전달되지 않는다. 알림 채널(QA-224)과 동일하게 Redis
 * pub/sub으로 발행하고, 각 인스턴스의 {@link DmRealtimeRelay}가 자신에게 연결된 세션으로 릴레이한다.
 *
 * <p>활성 트랜잭션이 있으면 커밋 후(afterCommit) 발행 — 수신 클라이언트가 재조회 시 미커밋 데이터를 놓치는
 * 것을 방지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmRealtimePublisher {

    public static final String CHANNEL = "chat:dm:realtime";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 특정 유저의 STOMP user destination으로 payload를 전달한다.
     *
     * @param userId 수신 유저 ID
     * @param destination user destination (예: "/queue/dm", "/queue/dm/read", "/queue/dm/typing")
     * @param payload 직렬화 가능한 DTO
     */
    public void publishToUser(String userId, String destination, Object payload) {
        if (userId == null || destination == null || payload == null) {
            return;
        }

        String message;
        try {
            message =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "user_id", userId,
                                    "destination", destination,
                                    "body", payload));
        } catch (Exception e) {
            log.warn("DM 실시간 직렬화 실패: userId={}, error={}", userId, e.getMessage());
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            doPublish(userId, message);
                        }
                    });
        } else {
            doPublish(userId, message);
        }
    }

    private void doPublish(String userId, String message) {
        try {
            stringRedisTemplate.convertAndSend(CHANNEL, message);
            log.debug("DM 실시간 발행: userId={}", userId);
        } catch (Exception e) {
            log.warn("DM 실시간 발행 실패: userId={}, error={}", userId, e.getMessage());
        }
    }
}
