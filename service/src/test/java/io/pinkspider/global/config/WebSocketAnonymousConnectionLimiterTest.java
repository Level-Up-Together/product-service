package io.pinkspider.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

class WebSocketAnonymousConnectionLimiterTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zset = mock(ZSetOperations.class);

    private WebSocketAnonymousConnectionLimiter limiter;

    @BeforeEach
    void setUp() {
        when(redis.opsForZSet()).thenReturn(zset);
        limiter = new WebSocketAnonymousConnectionLimiter(redis, 2, 12);
    }

    @Test
    @DisplayName("LUT-705: IP 마다 상한까지 잡고 넘으면 false · 오래된 항목은 먼저 지운다 · IP·세션 없으면 통과 · Redis 오류는 통과")
    void acquire() {
        when(zset.zCard("ws:anon:1.2.3.4")).thenReturn(1L);
        assertThat(limiter.tryAcquire("1.2.3.4", "s1")).isTrue();
        verify(zset).removeRangeByScore(eq("ws:anon:1.2.3.4"), eq(0.0), anyDouble());
        verify(zset).add(eq("ws:anon:1.2.3.4"), eq("s1"), anyDouble());
        verify(redis).expire("ws:anon:1.2.3.4", Duration.ofHours(12));

        when(zset.zCard("ws:anon:1.2.3.4")).thenReturn(2L);
        assertThat(limiter.tryAcquire("1.2.3.4", "s2")).isFalse();
        verify(zset, never()).add(eq("ws:anon:1.2.3.4"), eq("s2"), anyDouble());

        assertThat(limiter.tryAcquire(null, "s3")).isTrue();
        assertThat(limiter.tryAcquire(" ", "s3")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4", null)).isTrue();
        assertThat(
                        new WebSocketAnonymousConnectionLimiter(redis, 0, 12)
                                .tryAcquire("1.2.3.4", "s4"))
                .isTrue();

        when(zset.zCard("ws:anon:5.6.7.8")).thenThrow(new IllegalStateException("redis"));
        assertThat(limiter.tryAcquire("5.6.7.8", "s5")).isTrue();
    }

    @Test
    @DisplayName("LUT-705: 끊기면 그 세션을 뺀다 · 빈 값은 무시 · Redis 오류는 삼킨다 · 리스너는 세션 attribute 의 IP 로 뺀다")
    void release() {
        limiter.release("1.2.3.4", "s1");
        verify(zset).remove("ws:anon:1.2.3.4", "s1");
        limiter.release(null, "s1");
        limiter.release("1.2.3.4", null);
        when(zset.remove(anyString(), anyString())).thenThrow(new IllegalStateException("redis"));
        limiter.release("9.9.9.9", "s9");

        WebSocketAnonymousConnectionLimiter mocked =
                mock(WebSocketAnonymousConnectionLimiter.class);
        WebSocketAnonymousDisconnectListener listener =
                new WebSocketAnonymousDisconnectListener(mocked);
        listener.onDisconnect(
                event("s7", Map.of(WebSocketCookieHandshakeInterceptor.ATTR_CLIENT_IP, "1.2.3.4")));
        verify(mocked).release("1.2.3.4", "s7");
        listener.onDisconnect(event("s8", Map.of()));
        listener.onDisconnect(event("s9", null));
        verify(mocked, never()).release(anyString(), eq("s8"));
        verify(mocked, never()).release(anyString(), eq("s9"));
    }

    private static SessionDisconnectEvent event(String sessionId, Map<String, Object> attributes) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId(sessionId);
        if (attributes != null) {
            accessor.setSessionAttributes(new java.util.HashMap<>(attributes));
        }
        return new SessionDisconnectEvent(
                new Object(),
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()),
                sessionId,
                CloseStatus.NORMAL);
    }
}
