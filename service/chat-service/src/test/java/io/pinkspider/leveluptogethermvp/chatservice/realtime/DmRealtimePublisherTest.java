package io.pinkspider.leveluptogethermvp.chatservice.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class DmRealtimePublisherTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private DmRealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DmRealtimePublisher(stringRedisTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("트랜잭션이 없으면 즉시 Redis 채널로 발행한다")
    void publishToUser_withoutTransaction_publishesImmediately() {
        // when
        publisher.publishToUser("user-1", "/queue/dm", Map.of("content", "안녕"));

        // then
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate)
            .convertAndSend(eq(DmRealtimePublisher.CHANNEL), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("\"user_id\":\"user-1\"");
        assertThat(payloadCaptor.getValue()).contains("\"destination\":\"/queue/dm\"");
        assertThat(payloadCaptor.getValue()).contains("안녕");
    }

    @Test
    @DisplayName("userId가 null이면 발행하지 않는다")
    void publishToUser_nullUserId_skips() {
        publisher.publishToUser(null, "/queue/dm", Map.of("content", "안녕"));

        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    @DisplayName("destination이 null이면 발행하지 않는다")
    void publishToUser_nullDestination_skips() {
        publisher.publishToUser("user-1", null, Map.of("content", "안녕"));

        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    @DisplayName("payload가 null이면 발행하지 않는다")
    void publishToUser_nullPayload_skips() {
        publisher.publishToUser("user-1", "/queue/dm", null);

        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());
    }
}
