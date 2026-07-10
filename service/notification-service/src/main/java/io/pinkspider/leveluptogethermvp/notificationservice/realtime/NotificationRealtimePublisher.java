package io.pinkspider.leveluptogethermvp.notificationservice.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 알림 실시간 발행기 (QA-224)
 * <p>
 * 알림 저장 직후 Redis pub/sub 채널로 발행한다. 멀티 인스턴스 환경에서 SimpleBroker는
 * 인스턴스별 메모리 브로커이므로, 각 인스턴스의 {@link NotificationRealtimeRelay}가 이 채널을
 * 구독해 자신에게 연결된 WebSocket 세션으로 릴레이한다.
 * <p>
 * 트랜잭션 커밋 이전에 클라이언트가 재조회하면 미커밋 데이터를 놓치므로, 활성 트랜잭션이 있으면
 * afterCommit 시점에 발행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRealtimePublisher {

    public static final String CHANNEL = "notification:realtime";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String userId, NotificationResponse notification) {
        if (userId == null || notification == null) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                "user_id", userId,
                "notification", notification
            ));
        } catch (Exception e) {
            log.warn("실시간 알림 직렬화 실패: userId={}, error={}", userId, e.getMessage());
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublish(userId, payload);
                }
            });
        } else {
            doPublish(userId, payload);
        }
    }

    private void doPublish(String userId, String payload) {
        try {
            stringRedisTemplate.convertAndSend(CHANNEL, payload);
            log.debug("실시간 알림 발행: userId={}", userId);
        } catch (Exception e) {
            // 실시간 채널 실패는 폴링 폴백이 있으므로 알림 생성 자체를 실패시키지 않는다
            log.warn("실시간 알림 발행 실패: userId={}, error={}", userId, e.getMessage());
        }
    }
}
