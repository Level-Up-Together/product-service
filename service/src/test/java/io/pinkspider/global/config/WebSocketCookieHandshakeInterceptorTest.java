package io.pinkspider.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;

class WebSocketCookieHandshakeInterceptorTest {

    private WebSocketCookieHandshakeInterceptor interceptorWithCookieName(String cookieName) {
        WebSocketCookieHandshakeInterceptor interceptor = new WebSocketCookieHandshakeInterceptor();
        ReflectionTestUtils.setField(interceptor, "accessTokenCookieName", cookieName);
        return interceptor;
    }

    private ServletServerHttpRequest requestWithCookies(Cookie... cookies) {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getCookies()).thenReturn(cookies);
        return new ServletServerHttpRequest(servletRequest);
    }

    @Test
    @DisplayName("설정된 쿠키 이름과 일치하는 쿠키 값을 세션 attribute 로 전달한다")
    void attachesConfiguredCookie() {
        WebSocketCookieHandshakeInterceptor interceptor =
                interceptorWithCookieName("dev_access_token");
        ServletServerHttpRequest request =
                requestWithCookies(new Cookie("dev_access_token", "tok-123"));
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, null, null, attributes);

        assertThat(result).isTrue();
        assertThat(attributes)
                .containsEntry(WebSocketCookieHandshakeInterceptor.ATTR_ACCESS_TOKEN, "tok-123");
    }

    @Test
    @DisplayName("설정값과 다른 이름의 쿠키는 무시한다 (이름 불일치 시 인증 누락 방지 검증)")
    void ignoresCookieWithDifferentName() {
        WebSocketCookieHandshakeInterceptor interceptor = interceptorWithCookieName("access_token");
        ServletServerHttpRequest request =
                requestWithCookies(new Cookie("dev_access_token", "tok-123"));
        Map<String, Object> attributes = new HashMap<>();

        interceptor.beforeHandshake(request, null, null, attributes);

        assertThat(attributes)
                .doesNotContainKey(WebSocketCookieHandshakeInterceptor.ATTR_ACCESS_TOKEN);
    }
}
