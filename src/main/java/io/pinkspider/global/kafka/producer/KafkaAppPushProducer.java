package io.pinkspider.global.kafka.producer;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.KafkaSendMessageFailException;
import io.pinkspider.global.kafka.dto.AppPushMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaAppPushProducer {

    @Value(value = "${spring.kafka.app-push.topic}")
    private String topicName;

    @Autowired
    @Qualifier("appPushProducer")
    private KafkaTemplate<String, AppPushMessageDto> appPushKafkaTemplate;

    /**
     * 푸시 알림 메시지 전송
     */
    public void sendMessage(AppPushMessageDto appPushMessageDto) {
        CompletableFuture<SendResult<String, AppPushMessageDto>> future =
                appPushKafkaTemplate.send(topicName, appPushMessageDto);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("{} Sent Object=[{}] with offset=[{}]",
                        topicName, appPushMessageDto.toString(), result.getRecordMetadata().offset());
            } else {
                log.error("Unable to send Object=[{}] due to : {}",
                        appPushMessageDto.toString(), ex.getMessage());
                throw new KafkaSendMessageFailException(
                        ApiStatus.KAFKA_MESSAGE_ERROR.getResultCode(),
                        ApiStatus.KAFKA_MESSAGE_ERROR.getResultMessage());
            }
        });
    }

    /**
     * 단일 사용자에게 푸시 알림 전송
     */
    public void sendToUser(String userId, String title, String body) {
        sendMessage(AppPushMessageDto.forUser(userId, title, body));
    }

    /**
     * 토픽 기반 푸시 알림 전송 (길드 등)
     */
    public void sendToTopic(String topic, String title, String body) {
        sendMessage(AppPushMessageDto.forTopic(topic, title, body));
    }
}
