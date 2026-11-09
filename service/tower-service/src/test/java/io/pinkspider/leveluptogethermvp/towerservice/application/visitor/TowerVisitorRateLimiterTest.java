package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

class TowerVisitorRateLimiterTest {
    private static final long NOW = 1_800_000_000_000L;
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zset = mock(ZSetOperations.class);

    private final TowerVisitorProperties properties = new TowerVisitorProperties();
    private TowerVisitorRateLimiter limiter;
    private final String minute = ":" + (NOW / 60_000);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForZSet()).thenReturn(zset);
        limiter = new TowerVisitorRateLimiter(redis, properties, () -> NOW);
    }

    @Test
    @DisplayName("LUT-705 입장: IP 없으면 통과 · 1분 20건 넘으면 거절 · 새 세션이 IP 상한(10)에 닿으면 거절, 쓰던 세션은 통과")
    void enter() {
        assertThat(limiter.allowEnter(null, "vk")).isTrue();

        String enterKey = "tower:visitor:enter:1.2.3.4" + minute;
        when(values.increment(enterKey)).thenReturn(1L);
        when(zset.score("tower:visitor:ip:1.2.3.4", "vk")).thenReturn(null);
        when(zset.zCard("tower:visitor:ip:1.2.3.4")).thenReturn(3L);
        assertThat(limiter.allowEnter("1.2.3.4", "vk")).isTrue();
        verify(redis).expire(enterKey, Duration.ofMinutes(2));
        verify(zset).removeRangeByScore("tower:visitor:ip:1.2.3.4", 0, NOW - 600_000);
        verify(zset).add("tower:visitor:ip:1.2.3.4", "vk", NOW);

        when(values.increment(enterKey)).thenReturn(21L);
        assertThat(limiter.allowEnter("1.2.3.4", "vk")).isFalse();

        when(values.increment(enterKey)).thenReturn(2L);
        when(zset.zCard("tower:visitor:ip:1.2.3.4")).thenReturn(10L);
        when(zset.score("tower:visitor:ip:1.2.3.4", "vk-new"))
                .thenReturn(null); // 모의 객체 기본값 0.0 이 아니라 "없음"
        assertThat(limiter.allowEnter("1.2.3.4", "vk-new")).isFalse();
        when(zset.score("tower:visitor:ip:1.2.3.4", "vk")).thenReturn(1.0);
        assertThat(limiter.allowEnter("1.2.3.4", "vk")).isTrue();
    }

    @Test
    @DisplayName("LUT-705 채팅: IP 1분 12건까지 · IP 없으면 통과 · Redis 오류는 통과")
    void chat() {
        assertThat(limiter.allowChat(null)).isTrue();
        String key = "tower:visitor:chat:5.6.7.8" + minute;
        when(values.increment(key)).thenReturn(12L);
        assertThat(limiter.allowChat("5.6.7.8")).isTrue();
        verify(redis, never()).expire(eq(key), eq(Duration.ofMinutes(2)));
        when(values.increment(key)).thenReturn(13L);
        assertThat(limiter.allowChat("5.6.7.8")).isFalse();
        when(values.increment(key)).thenReturn(null);
        assertThat(limiter.allowChat("5.6.7.8")).isTrue();

        when(values.increment(anyString())).thenThrow(new IllegalStateException("redis"));
        assertThat(limiter.allowChat("5.6.7.8")).isTrue();
        assertThat(limiter.allowEnter("5.6.7.8", "vk")).isTrue();
        assertThat(new TowerVisitorRateLimiter(redis, properties).allowChat(null)).isTrue();
    }
}
