package io.pinkspider.leveluptogethermvp.notificationservice.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.messaging.dto.AppPushMessageDto;
import io.pinkspider.leveluptogethermvp.notificationservice.application.FcmPushService;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.PushMessageRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

/**
 * 푸시 알림 Redis Stream Consumer
 * Redis Stream 메시지를 받아서 FCM으로 실제 푸시 알림 전송
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AppPushMessageConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final FcmPushService fcmPushService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        try {
            String payload = record.getValue().get("payload");
            if (payload == null) {
                log.warn("Redis Stream 메시지 payload가 null: id={}", record.getId());
                return;
            }
            AppPushMessageDto message = objectMapper.readValue(payload, AppPushMessageDto.class);
            log.info("Redis Stream 메시지 수신: id={}, message={}", record.getId(), message);

            PushMessageRequest pushRequest = PushMessageRequest.full(
                    message.getTitle(),
                    message.getBody(),
                    message.getImageUrl(),
                    message.getClickAction(),
                    message.getData()
            );

            // 토픽 기반 전송
            if (message.getTopic() != null && !message.getTopic().isEmpty()) {
                fcmPushService.sendToTopic(message.getTopic(), pushRequest);
                return;
            }

            // 여러 사용자 전송
            List<String> userIds = message.getUserIds();
            if (userIds != null && !userIds.isEmpty()) {
                fcmPushService.sendToUsers(userIds, pushRequest);
                return;
            }

            // 단일 사용자 전송
            if (message.getUserId() != null && !message.getUserId().isEmpty()) {
                fcmPushService.sendToUser(message.getUserId(), pushRequest);
            }

        } catch (Exception e) {
            log.error("푸시 메시지 처리 실패: record={}", record.getId(), e);
        }
    }
}
