package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
@DisplayName("TowerChatGuard (LUT-633) — 전송 간격·중복·MEDIUM 반복 제한")
class TowerChatGuardTest {
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    private TowerChatGuard guard;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        guard = new TowerChatGuard(redis, new TowerChatProperties());
    }

    @Test
    @DisplayName("로그인 1초·방문객 5초 슬롯, 못 잡으면 false, Redis 오류는 통과")
    void acquire() {
        when(values.setIfAbsent(eq("tower:chat:rate:7"), eq("1"), eq(Duration.ofMillis(1000))))
                .thenReturn(true, false);
        assertThat(guard.tryAcquireSend("7", false)).isTrue();
        assertThat(guard.tryAcquireSend("7", false)).isFalse();
        when(values.setIfAbsent(eq("tower:chat:rrate:8"), eq("1"), eq(Duration.ofMillis(5000))))
                .thenReturn(true);
        assertThat(guard.tryAcquireReaction("8", true)).isTrue();
        when(values.setIfAbsent(eq("tower:chat:rate:9"), eq("1"), any(Duration.class)))
                .thenThrow(new RuntimeException("down"));
        assertThat(guard.tryAcquireSend("9", false)).isTrue();
    }

    @Test
    @DisplayName("같은 내용 연속 전송은 직전 해시로 판정(앞뒤 공백 무시), 오류면 중복 아님")
    void duplicate() {
        guard.rememberContent("7", " 안녕 ");
        verify(values)
                .set(
                        eq("tower:chat:last:7"),
                        eq(TowerChatGuard.digest("안녕")),
                        eq(Duration.ofSeconds(60)));
        when(values.get("tower:chat:last:7")).thenReturn(TowerChatGuard.digest("안녕"));
        assertThat(guard.isDuplicate("7", "안녕")).isTrue();
        assertThat(guard.isDuplicate("7", "안녕!")).isFalse();
        when(values.get("tower:chat:last:8")).thenThrow(new RuntimeException("down"));
        assertThat(guard.isDuplicate("8", "x")).isFalse();
        when(values.get("tower:chat:last:9")).thenReturn(null);
        assertThat(guard.isDuplicate("9", "x")).isFalse();
    }

    @Test
    @DisplayName("MEDIUM 3회(60초 창)면 300초 제한을 걸고 카운터를 지운다")
    void mediumRepeat() {
        when(values.increment("tower:chat:medium:7")).thenReturn(1L, 2L, 3L);
        assertThat(guard.recordMediumHit("7")).isFalse();
        verify(redis).expire("tower:chat:medium:7", Duration.ofSeconds(60));
        assertThat(guard.recordMediumHit("7")).isFalse();
        assertThat(guard.recordMediumHit("7")).isTrue();
        verify(values).set("tower:chat:mute:7", "1", Duration.ofSeconds(300));
        verify(redis).delete("tower:chat:medium:7");

        when(redis.hasKey("tower:chat:mute:7")).thenReturn(true);
        assertThat(guard.isMuted("7")).isTrue();
        when(redis.hasKey("tower:chat:mute:8")).thenThrow(new RuntimeException("down"));
        assertThat(guard.isMuted("8")).isFalse();
        when(values.increment("tower:chat:medium:9")).thenThrow(new RuntimeException("down"));
        assertThat(guard.recordMediumHit("9")).isFalse();
        verify(values, never()).set(eq("tower:chat:mute:9"), anyString(), any(Duration.class));
    }
}
