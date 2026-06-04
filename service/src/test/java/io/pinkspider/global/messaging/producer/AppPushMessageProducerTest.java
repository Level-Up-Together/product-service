package io.pinkspider.global.messaging.producer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.exception.MessagingSendFailException;
import io.pinkspider.global.messaging.dto.AppPushMessageDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppPushMessageProducer 단위 테스트")
class AppPushMessageProducerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @InjectMocks
    private AppPushMessageProducer appPushMessageProducer;

    @Nested
    @DisplayName("sendMessage 테스트")
    class SendMessageTest {

        @Test
        @DisplayName("AppPushMessageDto를 Redis Stream에 발행한다")
        void sendMessage_success() throws Exception {
            // given
            AppPushMessageDto dto = AppPushMessageDto.forUser("user-1", "제목", "내용");
            String payload = "{\"user_id\":\"user-1\",\"title\":\"제목\",\"body\":\"내용\"}";
            RecordId recordId = RecordId.of("1234567890-0");

            when(objectMapper.writeValueAsString(dto)).thenReturn(payload);
            when(stringRedisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
            when(streamOperations.add(any(StringRecord.class))).thenReturn(recordId);

            // when
            appPushMessageProducer.sendMessage(dto);

            // then
            verify(objectMapper).writeValueAsString(dto);
            verify(streamOperations).add(any(StringRecord.class));
        }

        @Test
        @DisplayName("JSON 직렬화 실패 시 MessagingSendFailException을 던진다")
        void sendMessage_jsonSerializationFailure() throws Exception {
            // given
            AppPushMessageDto dto = AppPushMessageDto.forUser("user-1", "제목", "내용");
            when(objectMapper.writeValueAsString(dto)).thenThrow(new JsonProcessingException("직렬화 실패") {});

            // when & then
            assertThatThrownBy(() -> appPushMessageProducer.sendMessage(dto))
                    .isInstanceOf(MessagingSendFailException.class);
        }

        @Test
        @DisplayName("Redis 연결 실패 시 MessagingSendFailException을 던진다")
        void sendMessage_redisFailure() throws Exception {
            // given
            AppPushMessageDto dto = AppPushMessageDto.forUser("user-1", "제목", "내용");
            String payload = "{\"user_id\":\"user-1\"}";

            when(objectMapper.writeValueAsString(dto)).thenReturn(payload);
            when(stringRedisTemplate.opsForStream()).thenThrow(new RuntimeException("Redis 연결 실패"));

            // when & then
            assertThatThrownBy(() -> appPushMessageProducer.sendMessage(dto))
                    .isInstanceOf(MessagingSendFailException.class);
        }
    }

    @Nested
    @DisplayName("sendToUser 테스트")
    class SendToUserTest {

        @Test
        @DisplayName("단일 사용자에게 푸시 알림을 발행한다")
        void sendToUser_success() throws Exception {
            // given
            String userId = "test-user-123";
            String title = "알림 제목";
            String body = "알림 내용";
            String payload = "{\"user_id\":\"test-user-123\",\"title\":\"알림 제목\",\"body\":\"알림 내용\"}";
            RecordId recordId = RecordId.of("1234567890-0");

            when(objectMapper.writeValueAsString(any(AppPushMessageDto.class))).thenReturn(payload);
            when(stringRedisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
            when(streamOperations.add(any(StringRecord.class))).thenReturn(recordId);

            // when
            appPushMessageProducer.sendToUser(userId, title, body);

            // then
            verify(streamOperations).add(any(StringRecord.class));
        }

        @Test
        @DisplayName("sendToUser 호출 시 userId가 담긴 메시지가 발행된다")
        void sendToUser_containsUserId() throws Exception {
            // given
            String userId = "user-abc";
            String payload = "{\"user_id\":\"user-abc\"}";
            RecordId recordId = RecordId.of("1234567890-0");

            when(objectMapper.writeValueAsString(any(AppPushMessageDto.class))).thenReturn(payload);
            when(stringRedisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
            when(streamOperations.add(any(StringRecord.class))).thenReturn(recordId);

            // when
            appPushMessageProducer.sendToUser(userId, "title", "body");

            // then
            verify(objectMapper).writeValueAsString(any(AppPushMessageDto.class));
        }
    }

    @Nested
    @DisplayName("sendToTopic 테스트")
    class SendToTopicTest {

        @Test
        @DisplayName("토픽 기반 푸시 알림을 발행한다")
        void sendToTopic_success() throws Exception {
            // given
            String topic = "guild-123";
            String title = "길드 알림";
            String body = "길드 미션이 시작되었습니다.";
            String payload = "{\"topic\":\"guild-123\",\"title\":\"길드 알림\",\"body\":\"길드 미션이 시작되었습니다.\"}";
            RecordId recordId = RecordId.of("1234567890-0");

            when(objectMapper.writeValueAsString(any(AppPushMessageDto.class))).thenReturn(payload);
            when(stringRedisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
            when(streamOperations.add(any(StringRecord.class))).thenReturn(recordId);

            // when
            appPushMessageProducer.sendToTopic(topic, title, body);

            // then
            verify(streamOperations).add(any(StringRecord.class));
        }

        @Test
        @DisplayName("sendToTopic 호출 시 topic이 담긴 메시지가 발행된다")
        void sendToTopic_containsTopic() throws Exception {
            // given
            String payload = "{\"topic\":\"guild-456\"}";
            RecordId recordId = RecordId.of("1234567890-0");

            when(objectMapper.writeValueAsString(any(AppPushMessageDto.class))).thenReturn(payload);
            when(stringRedisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
            when(streamOperations.add(any(StringRecord.class))).thenReturn(recordId);

            // when
            appPushMessageProducer.sendToTopic("guild-456", "title", "body");

            // then
            verify(objectMapper).writeValueAsString(any(AppPushMessageDto.class));
        }
    }
}
