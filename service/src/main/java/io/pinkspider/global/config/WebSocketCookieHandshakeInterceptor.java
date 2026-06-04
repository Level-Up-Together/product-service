package io.pinkspider.global.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket handshake 단계에서 HTTP 쿠키의 access_token 을 추출해 STOMP 세션 attributes 에 저장한다. httpOnly 쿠키는 JS
 * 가 만질 수 없으므로 STOMP CONNECT native header 로 토큰을 보낼 수 없는데, 이 인터셉터로 핸드셰이크 시점에 쿠키 → attributes 로 전달하면
 * WebSocketAuthInterceptor 가 fallback 으로 사용할 수 있다.
 */
@Slf4j
@Component
@Profile("!test")
public class WebSocketCookieHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_ACCESS_TOKEN = "access_token";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            Cookie[] cookies = httpRequest.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (ATTR_ACCESS_TOKEN.equals(cookie.getName())) {
                        String value = cookie.getValue();
                        if (value != null && !value.isEmpty()) {
                            attributes.put(ATTR_ACCESS_TOKEN, value);
                            log.debug(
                                    "WS handshake: access_token cookie attached to session"
                                            + " attributes");
                        }
                        break;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
