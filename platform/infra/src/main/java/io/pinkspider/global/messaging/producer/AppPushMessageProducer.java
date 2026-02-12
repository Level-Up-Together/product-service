package io.pinkspider.global.messaging.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.MessagingSendFailException;
import io.pinkspider.global.messaging.dto.AppPushMessageDto;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AppPushMessageProducer {

    private static final String STREAM_KEY = "stream:app-push";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 푸시 알림 메시지를 Redis Stream에 발행
     */
    public void sendMessage(AppPushMessageDto appPushMessageDto) {
        try {
            String payload = objectMapper.writeValueAsString(appPushMessageDto);
            StringRecord record = StringRecord.of(Map.of("payload", payload))
                    .withStreamKey(STREAM_KEY);

            RecordId recordId = stringRedisTemplate.opsForStream().add(record);
            log.info("Redis Stream 메시지 발행: stream={}, id={}, message={}",
                    STREAM_KEY, recordId, appPushMessageDto);
        } catch (JsonProcessingException e) {
            log.error("메시지 직렬화 실패: {}", appPushMessageDto, e);
            throw new MessagingSendFailException(
                    ApiStatus.MESSAGING_SEND_FAIL.getResultCode(),
                    ApiStatus.MESSAGING_SEND_FAIL.getResultMessage());
        } catch (Exception e) {
            log.error("Redis Stream 메시지 발행 실패: {}", appPushMessageDto, e);
            throw new MessagingSendFailException(
                    ApiStatus.MESSAGING_SEND_FAIL.getResultCode(),
                    ApiStatus.MESSAGING_SEND_FAIL.getResultMessage());
        }
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
