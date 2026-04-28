package io.pinkspider.leveluptogethermvp.notificationservice.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.messaging.dto.AppPushMessageDto;
import io.pinkspider.leveluptogethermvp.notificationservice.application.FcmPushService;
import io.pinkspider.leveluptogethermvp.notificationservice.application.NotificationService;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.PushMessageRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

/**
 * 푸시 알림 Redis Stream Consumer
 * Redis Stream 메시지를 받아서 FCM으로 실제 푸시 알림 전송 + 일부 타입은 in-app 알림 DB에도 저장.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AppPushMessageConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final String NOTIFICATION_TYPE_INQUIRY_REPLIED = "INQUIRY_REPLIED";

    private final FcmPushService fcmPushService;
    private final NotificationService notificationService;
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

            // 외부 서비스(admin-service)에서 들어온 type별 후속 처리 (QA-94: in-app DB 저장)
            handleExternalNotificationType(message);

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

    /**
     * notification_type별 in-app DB 저장 등 후속 처리.
     * NotificationService 자체 흐름(이미 in-app 저장 + push 발행 둘 다 함)과는 별도로,
     * 외부 서비스에서 stream으로 들어온 것만 여기서 처리한다.
     */
    private void handleExternalNotificationType(AppPushMessageDto message) {
        String type = message.getNotificationType();
        if (type == null) return;

        try {
            switch (type) {
                case NOTIFICATION_TYPE_INQUIRY_REPLIED -> {
                    String userId = message.getUserId();
                    Long inquiryId = parseLong(message.getData(), "inquiry_id");
                    String inquiryTitle = message.getBody();
                    if (userId != null && inquiryId != null) {
                        notificationService.saveInquiryRepliedInApp(userId, inquiryId, inquiryTitle);
                    }
                }
                default -> {
                    // 그 외 type은 push 전송만 (NotificationService가 발행한 것은 이미 in-app 저장된 상태)
                }
            }
        } catch (Exception e) {
            log.error("외부 알림 type 처리 실패: type={}, error={}", type, e.getMessage(), e);
        }
    }

    private static Long parseLong(Map<String, String> data, String key) {
        if (data == null) return null;
        String value = data.get(key);
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
