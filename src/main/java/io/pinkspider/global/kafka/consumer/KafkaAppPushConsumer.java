package io.pinkspider.global.kafka.consumer;

import io.pinkspider.global.kafka.dto.AppPushMessageDto;
import io.pinkspider.leveluptogethermvp.notificationservice.application.FcmPushService;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.PushMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 푸시 알림 Kafka Consumer
 * Kafka 메시지를 받아서 FCM으로 실제 푸시 알림 전송
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaAppPushConsumer {

    private final FcmPushService fcmPushService;

    @KafkaListener(
            topics = "${spring.kafka.app-push.topic}",
            groupId = "${spring.kafka.app-push.group-id:app-push-consumer-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(AppPushMessageDto message) {
        log.info("Received push message: {}", message);

        try {
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
            log.error("Failed to process push message: {}", message, e);
        }
    }
}
