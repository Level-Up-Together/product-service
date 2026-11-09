package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TowerRoomMoveLimiterTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final TowerRoomMoveLimiter limiter = new TowerRoomMoveLimiter(redis);

    @Test
    @DisplayName(
            "LUT-738 남은 초: 키 없음(-2)·만료 없음(-1)·null 은 0, 1초 미만으로 남으면 1 · 이동하면 10초 표시 · Redis 장애는 통과")
    void cooldown() {
        when(redis.getExpire("tower:room-move:u:u1")).thenReturn(7L, -2L, -1L, null, 0L);
        assertThat(limiter.remainingSeconds("u:u1")).isEqualTo(7);
        assertThat(limiter.remainingSeconds("u:u1")).isZero();
        assertThat(limiter.remainingSeconds("u:u1")).isZero();
        assertThat(limiter.remainingSeconds("u:u1")).isZero();
        assertThat(limiter.remainingSeconds("u:u1")).isEqualTo(1);

        when(redis.opsForValue()).thenReturn(ops);
        limiter.markMoved("v:key");
        verify(ops).set("tower:room-move:v:key", "1", Duration.ofSeconds(10));

        when(redis.getExpire(anyString())).thenThrow(new IllegalStateException("down"));
        assertThat(limiter.remainingSeconds("u:u2")).isZero();
        doThrow(new IllegalStateException("down"))
                .when(ops)
                .set(anyString(), anyString(), any(Duration.class));
        assertThatCode(() -> limiter.markMoved("u:u2")).doesNotThrowAnyException();
    }
}
