package io.pinkspider.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.security.JwtUtil;
import io.pinkspider.global.security.TokenBlacklistChecker;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class WebSocketAuthInterceptorTest {
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final TokenBlacklistChecker blacklist = mock(TokenBlacklistChecker.class);
    private final WebSocketAnonymousConnectionLimiter limiter =
            mock(WebSocketAnonymousConnectionLimiter.class);
    private final WebSocketAuthInterceptor interceptor =
            new WebSocketAuthInterceptor(jwtUtil, blacklist, limiter);

    private static Message<byte[]> connect(String token, String ip) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("s1");
        if (token != null) {
            accessor.addNativeHeader("Authorization", "Bearer " + token);
        }
        Map<String, Object> attributes = new HashMap<>();
        if (ip != null) {
            attributes.put(WebSocketCookieHandshakeInterceptor.ATTR_CLIENT_IP, ip);
        }
        accessor.setSessionAttributes(attributes);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("LUT-705: 토큰이 유효하면 인증하고 비로그인 제한을 거치지 않는다")
    void authenticated() {
        when(jwtUtil.validateToken("t")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("t")).thenReturn("u1");
        Message<?> out = interceptor.preSend(connect("t", "1.2.3.4"), null);
        assertThat(StompHeaderAccessor.wrap(out).getUser().getName()).isEqualTo("u1");
        verify(limiter, never()).tryAcquire(any(), any());
    }

    @Test
    @DisplayName("LUT-705: 토큰 없음·무효 토큰은 비로그인 연결 — IP 별 상한이면 CONNECT 거절, 아니면 통과")
    void anonymous() {
        when(limiter.tryAcquire("1.2.3.4", "s1")).thenReturn(true);
        assertThat(interceptor.preSend(connect(null, "1.2.3.4"), null)).isNotNull();

        when(jwtUtil.validateToken("bad")).thenReturn(false);
        when(limiter.tryAcquire("5.6.7.8", "s1")).thenReturn(false);
        assertThatThrownBy(() -> interceptor.preSend(connect("bad", "5.6.7.8"), null))
                .isInstanceOf(MessageDeliveryException.class);

        when(limiter.tryAcquire(null, "s1")).thenReturn(true);
        assertThat(interceptor.preSend(connect(null, null), null)).isNotNull();
    }

    @Test
    @DisplayName("CONNECT 가 아닌 프레임은 그대로")
    void otherFrames() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThat(interceptor.preSend(message, null)).isSameAs(message);
        verify(limiter, never()).tryAcquire(any(), any());
    }
}
