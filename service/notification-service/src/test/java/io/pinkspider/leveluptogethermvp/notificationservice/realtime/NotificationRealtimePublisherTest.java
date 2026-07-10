package io.pinkspider.leveluptogethermvp.notificationservice.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.pinkspider.global.enums.NotificationType;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationRealtimePublisherTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private NotificationRealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new NotificationRealtimePublisher(stringRedisTemplate, objectMapper);
    }

    private NotificationResponse createResponse() {
        return NotificationResponse.builder()
            .id(1L)
            .notificationType(NotificationType.SYSTEM)
            .title("테스트 알림")
            .message("테스트 메시지")
            .isRead(false)
            .createdAt(LocalDateTime.of(2026, 7, 10, 2, 0, 0))
            .build();
    }

    @Test
    @DisplayName("트랜잭션이 없으면 즉시 Redis 채널로 발행한다")
    void publish_withoutTransaction_publishesImmediately() {
        // when
        publisher.publish("user-1", createResponse());

        // then
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate)
            .convertAndSend(eq(NotificationRealtimePublisher.CHANNEL), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("\"user_id\":\"user-1\"");
        assertThat(payloadCaptor.getValue()).contains("테스트 알림");
    }

    @Test
    @DisplayName("userId가 null이면 발행하지 않는다")
    void publish_nullUserId_skips() {
        publisher.publish(null, createResponse());

        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    @DisplayName("notification이 null이면 발행하지 않는다")
    void publish_nullNotification_skips() {
        publisher.publish("user-1", null);

        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());
    }
}
