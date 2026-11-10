package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerChatMessage;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("TowerChatHistoryStore (LUT-633) — Redis 리스트 최근 50건")
class TowerChatHistoryStoreTest {
    @Mock private StringRedisTemplate redis;
    @Mock private ListOperations<String, String> list;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private TowerChatHistoryStore store;

    @BeforeEach
    void setUp() {
        when(redis.opsForList()).thenReturn(list);
        store = new TowerChatHistoryStore(redis, mapper, new TowerChatProperties());
    }

    private static TowerChatMessage msg(String id) {
        return new TowerChatMessage(
                id,
                5L,
                7L,
                "u1",
                null,
                "닉",
                "안녕",
                false,
                LocalDateTime.of(2026, 11, 4, 13, 0),
                null);
    }

    @Test
    @DisplayName("push 는 앞에 넣고 50건으로 자르고 TTL 24시간")
    void push() {
        store.push(msg("a"));
        verify(list).leftPush(eq("tower:chat:history:5"), anyString());
        verify(list).trim("tower:chat:history:5", 0, 49);
        verify(redis).expire("tower:chat:history:5", Duration.ofHours(24));
    }

    @Test
    @DisplayName("recent 는 오래된 것부터 돌려주고, 깨진 항목은 건너뛰며, 오류·없음은 빈 목록")
    void recent() throws Exception {
        when(list.range("tower:chat:history:5", 0, 49))
                .thenReturn(
                        List.of(
                                mapper.writeValueAsString(msg("newest")),
                                "broken",
                                mapper.writeValueAsString(msg("oldest"))));
        List<TowerChatMessage> messages = store.recent(5L);
        assertThat(messages).extracting(TowerChatMessage::id).containsExactly("oldest", "newest");
        when(list.range("tower:chat:history:6", 0, 49)).thenReturn(List.of());
        assertThat(store.recent(6L)).isEmpty();
        when(list.range("tower:chat:history:7", 0, 49)).thenThrow(new RuntimeException("down"));
        assertThat(store.recent(7L)).isEmpty();
    }
}
