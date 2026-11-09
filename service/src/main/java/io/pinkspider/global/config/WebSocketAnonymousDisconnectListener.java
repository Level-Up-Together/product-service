package io.pinkspider.global.config;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** LUT-705: 비로그인 연결이 끊기면 IP 별 연결 수에서 뺀다(로그인 연결은 애초에 넣지 않아 지워도 무해) */
@Component
@RequiredArgsConstructor
public class WebSocketAnonymousDisconnectListener {
    private final WebSocketAnonymousConnectionLimiter limiter;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Map<String, Object> attributes =
                SimpMessageHeaderAccessor.getSessionAttributes(event.getMessage().getHeaders());
        if (attributes == null) {
            return;
        }
        Object ip = attributes.get(WebSocketCookieHandshakeInterceptor.ATTR_CLIENT_IP);
        if (ip instanceof String value) {
            limiter.release(value, event.getSessionId());
        }
    }
}
