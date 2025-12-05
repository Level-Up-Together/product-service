package io.pinkspider.global.kafka.producer;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.KafkaSendMessageFailException;
import io.pinkspider.global.kafka.dto.KafkaLoggerMessageDto;
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
public class KafkaLoggerProducer {

    @Value(value = "${spring.kafka.logger.topic}")
    private String topicName;

    @Autowired
    @Qualifier("loggerProducer")
    private KafkaTemplate<String, KafkaLoggerMessageDto> loggerKafkaTemplate;

    public void sendLoggerMessage(KafkaLoggerMessageDto kafkaLoggerMessageDto) {
        CompletableFuture<SendResult<String, KafkaLoggerMessageDto>> future = loggerKafkaTemplate.send(topicName, kafkaLoggerMessageDto);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info(
                    topicName + " Sent Object=[" + kafkaLoggerMessageDto.toString() + "] with offset=[" + result.getRecordMetadata().offset() + "]");
            } else {
                log.info("Unable to send Object=[" + kafkaLoggerMessageDto.toString() + "] due to : " + ex.getMessage());
                throw new KafkaSendMessageFailException(ApiStatus.KAFKA_MESSAGE_ERROR.getResultCode(),
                    ApiStatus.KAFKA_MESSAGE_ERROR.getResultMessage());
            }
        });
    }
}
