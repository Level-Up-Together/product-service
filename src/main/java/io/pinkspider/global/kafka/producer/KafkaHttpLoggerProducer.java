package io.pinkspider.global.kafka.producer;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.KafkaSendMessageFailException;
import io.pinkspider.global.kafka.dto.KafkaHttpLoggerMessageDto;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaHttpLoggerProducer {

    @Value(value = "${spring.kafka.http-logger.topic}")
    private String topicName;

    @Autowired
    @Qualifier("httpLoggerProducer")
    private KafkaTemplate<String, KafkaHttpLoggerMessageDto> httpLoggerKafkaTemplate;

    public void sendHttpLoggerMessage(KafkaHttpLoggerMessageDto kafkaHttpLoggerMessageDto) {
        CompletableFuture<SendResult<String, KafkaHttpLoggerMessageDto>> future = httpLoggerKafkaTemplate.send(topicName, kafkaHttpLoggerMessageDto);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info(
                    topicName + " Sent Object=[" + kafkaHttpLoggerMessageDto.toString() + "] with offset=[" + result.getRecordMetadata().offset()
                        + "]");
            } else {
                log.info("Unable to send Object=[" + kafkaHttpLoggerMessageDto.toString() + "] due to : " + ex.getMessage());
                throw new KafkaSendMessageFailException(ApiStatus.KAFKA_MESSAGE_ERROR.getResultCode(),
                    ApiStatus.KAFKA_MESSAGE_ERROR.getResultMessage());
            }
        });
    }
}
