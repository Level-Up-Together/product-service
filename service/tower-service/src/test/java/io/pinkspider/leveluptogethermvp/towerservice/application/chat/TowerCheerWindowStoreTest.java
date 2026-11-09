package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("TowerCheerWindowStore (LUT-633) — 완료 후 10초 응원 창, 한 사람 1회")
class TowerCheerWindowStoreTest {
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    private TowerCheerWindowStore store;

    @BeforeEach
    void setUp() {
        store = new TowerCheerWindowStore(redis);
    }

    @Test
    void openAndCheck() {
        when(redis.opsForValue()).thenReturn(values);
        store.open(5L, "u1");
        verify(values).set("tower:cheer:window:5:u1", "1", Duration.ofSeconds(10));
        when(redis.hasKey("tower:cheer:window:5:u1")).thenReturn(true);
        assertThat(store.isOpen(5L, "u1")).isTrue();
        when(values.setIfAbsent(eq("tower:cheer:done:5:u1:7"), eq("1"), any(Duration.class)))
                .thenReturn(true, false);
        assertThat(store.markCheered(5L, "u1", 7L)).isTrue();
        assertThat(store.markCheered(5L, "u1", 7L)).isFalse();
    }

    @Test
    void redisDown() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("down"));
        store.open(5L, "u1");
        assertThat(store.markCheered(5L, "u1", 7L)).isFalse();
        when(redis.hasKey("tower:cheer:window:5:u1")).thenThrow(new RuntimeException("down"));
        assertThat(store.isOpen(5L, "u1")).isFalse();
    }
}
