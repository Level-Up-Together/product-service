package io.pinkspider.leveluptogethermvp.notificationservice.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.enums.NotificationType;
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

            // QA-94: 일부 type은 사용자 locale로 push 텍스트 i18n 재구성
            String[] localizedText = localizePushTextIfNeeded(message);
            String title = localizedText != null ? localizedText[0] : message.getTitle();
            String body = localizedText != null ? localizedText[1] : message.getBody();

            PushMessageRequest pushRequest = PushMessageRequest.full(
                    title,
                    body,
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

    /**
     * INQUIRY_REPLIED 등 외부에서 발행된 알림의 push 텍스트를 사용자 locale로 i18n 재구성.
     * NotificationType이 매핑되지 않거나 단일 사용자 대상이 아니면 null 반환 (원본 사용).
     */
    private String[] localizePushTextIfNeeded(AppPushMessageDto message) {
        String typeStr = message.getNotificationType();
        String userId = message.getUserId();
        if (typeStr == null || userId == null || userId.isBlank()) return null;

        NotificationType type;
        try {
            type = NotificationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null; // 알 수 없는 type은 원본 텍스트 사용
        }

        // 메시지 템플릿이 정의된 type만 i18n 재구성 대상
        if (type.getDefaultTitle() == null || type.getMessageTemplate() == null) return null;

        try {
            // INQUIRY_REPLIED의 경우 body에 inquiry_title이 들어있음 → message arg로 사용
            String[] result = notificationService.localizePushText(userId, type, message.getBody());
            log.debug("Push 텍스트 i18n 재구성: userId={}, type={}", userId, type);
            return result;
        } catch (Exception e) {
            log.warn("Push 텍스트 i18n 재구성 실패, 원본 사용: userId={}, type={}, error={}",
                userId, type, e.getMessage());
            return null;
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
