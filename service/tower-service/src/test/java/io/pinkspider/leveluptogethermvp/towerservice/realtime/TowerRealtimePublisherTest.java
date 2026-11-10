package io.pinkspider.leveluptogethermvp.towerservice.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerInstanceEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** LUT-632: 탑 인스턴스 토픽 발행 — Redis pub/sub 으로만 나간다(노드 로컬 브로커 직접 전송 금지) */
class TowerRealtimePublisherTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());
    private final TowerRealtimePublisher publisher =
            new TowerRealtimePublisher(redis, objectMapper);

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("destination 은 /topic/tower/instances/{id}, body 는 snake_case DTO")
    void publish() {
        publisher.publishToInstance(
                9L,
                TowerInstanceEvent.missionState(
                        "u1",
                        new TowerInstanceEvent.MissionInfo("달리기", 3L),
                        LocalDateTime.of(2026, 11, 3, 12, 0)));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(TowerRealtimePublisher.CHANNEL), captor.capture());
        assertThat(captor.getValue())
                .contains("\"destination\":\"/topic/tower/instances/9\"")
                .contains("\"user_id\":\"u1\"")
                .contains("\"category_id\":3")
                .contains("\"type\":\"USER_STATE\"")
                .doesNotContain("cheer_window_seconds");
    }

    @Test
    @DisplayName("활성 트랜잭션이 있으면 커밋 후에 발행한다")
    void afterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        publisher.publishToInstance(9L, TowerInstanceEvent.restState("u1", LocalDateTime.now()));
        verify(redis, never()).convertAndSend(anyString(), any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(redis).convertAndSend(eq(TowerRealtimePublisher.CHANNEL), anyString());
    }

    @Test
    @DisplayName("null·직렬화 실패·Redis 실패는 조용히 무시한다")
    void failuresAreSwallowed() {
        publisher.publishToInstance(null, TowerInstanceEvent.restState("u1", LocalDateTime.now()));
        publisher.publishToInstance(9L, null);
        publisher.publishToInstance(9L, new Object()); // FAIL_ON_EMPTY_BEANS → 직렬화 실패
        verify(redis, never()).convertAndSend(anyString(), any());

        doThrow(new RuntimeException("redis down")).when(redis).convertAndSend(anyString(), any());
        publisher.publishToInstance(9L, TowerInstanceEvent.restState("u1", LocalDateTime.now()));
        verify(redis).convertAndSend(eq(TowerRealtimePublisher.CHANNEL), anyString());
    }
}
