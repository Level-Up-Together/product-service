package io.pinkspider.leveluptogethermvp.chatservice.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ReadStatusUpdate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class GuildChatRealtimePublisherTest {

    @Mock private StringRedisTemplate stringRedisTemplate;

    private GuildChatRealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new GuildChatRealtimePublisher(stringRedisTemplate, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션이 없으면 즉시 Redis 채널로 destination+body를 발행한다")
    void publishToTopic_withoutTransaction_publishesImmediately() {
        publisher.publishToTopic("/topic/guild/1/chat", Map.of("content", "안녕"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate)
                .convertAndSend(eq(GuildChatRealtimePublisher.CHANNEL), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("\"destination\":\"/topic/guild/1/chat\"");
        assertThat(payloadCaptor.getValue()).contains("안녕");
    }

    @Test
    @DisplayName("@JsonNaming(SnakeCase) DTO는 snake_case로 직렬화된다 — 프론트 매칭 보장")
    void publishToTopic_snakeCaseDto() {
        publisher.publishToTopic("/topic/guild/1/read", new ReadStatusUpdate(1L, 100L, "u1", 2));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate)
                .convertAndSend(eq(GuildChatRealtimePublisher.CHANNEL), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("\"message_id\":100");
        assertThat(payloadCaptor.getValue()).contains("\"unread_count\":2");
        assertThat(payloadCaptor.getValue()).doesNotContain("messageId");
    }

    @Test
    @DisplayName("활성 트랜잭션이 있으면 커밋 후에 발행한다")
    void publishToTopic_withTransaction_publishesAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishToTopic("/topic/guild/1/chat", Map.of("content", "안녕"));
        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());

        for (TransactionSynchronization sync :
                TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(stringRedisTemplate)
                .convertAndSend(eq(GuildChatRealtimePublisher.CHANNEL), anyString());
    }

    @Test
    @DisplayName("destination이 null/blank면 발행하지 않는다")
    void publishToTopic_blankDestination_skips() {
        publisher.publishToTopic(null, Map.of("content", "안녕"));
        publisher.publishToTopic("  ", Map.of("content", "안녕"));

        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    @DisplayName("payload가 null이면 발행하지 않는다")
    void publishToTopic_nullPayload_skips() {
        publisher.publishToTopic("/topic/guild/1/chat", null);

        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    @DisplayName("직렬화 불가 payload는 경고만 남기고 발행하지 않는다")
    void publishToTopic_unserializablePayload_skips() {
        Object selfRef =
                new Object() {
                    @SuppressWarnings("unused")
                    public Object getSelf() {
                        return this;
                    }
                };

        publisher.publishToTopic("/topic/guild/1/chat", selfRef);

        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    @DisplayName("Redis 발행 실패는 예외를 전파하지 않는다 (채팅 저장은 이미 끝난 상태)")
    void publishToTopic_redisFailure_noThrow() {
        doThrow(new RuntimeException("redis down"))
                .when(stringRedisTemplate)
                .convertAndSend(anyString(), anyString());

        publisher.publishToTopic("/topic/guild/1/chat", Map.of("content", "안녕"));

        verify(stringRedisTemplate)
                .convertAndSend(eq(GuildChatRealtimePublisher.CHANNEL), anyString());
    }
}
