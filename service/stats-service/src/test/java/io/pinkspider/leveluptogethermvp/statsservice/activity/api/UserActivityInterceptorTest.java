package io.pinkspider.leveluptogethermvp.statsservice.activity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.web.DeviceTypeResolver;
import io.pinkspider.leveluptogethermvp.statsservice.activity.application.UserActivityRecorder;
import io.pinkspider.leveluptogethermvp.statsservice.activity.config.UserActivityProperties;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserActivityInterceptor 테스트 (LUT-598)")
class UserActivityInterceptorTest {

    @Mock private UserActivityRecorder recorder;
    @Mock private DeviceTypeResolver deviceTypeResolver;

    private UserActivityProperties properties;
    private UserActivityInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        properties = new UserActivityProperties();
        interceptor = new UserActivityInterceptor(recorder, properties, deviceTypeResolver);
        request = new MockHttpServletRequest("GET", "/api/v1/bff/home");
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Object principal) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    @DisplayName("인증된 유저 요청이면 기기 종류를 해석해 비동기 기록을 호출하고 요청을 통과시킨다")
    void recordsAuthenticatedUser() {
        authenticateAs("user-1");
        request.addHeader(UserActivityInterceptor.DEVICE_TYPE_HEADER, "ios");
        when(deviceTypeResolver.resolve(request, "ios")).thenReturn("ios");

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        verify(recorder).recordAsync("user-1", "ios");
    }

    @Test
    @DisplayName("헤더가 없으면 User-Agent 추정 결과(resolver 가 돌려준 값)를 그대로 쓴다")
    void resolvesWithoutHeader() {
        authenticateAs("user-1");
        when(deviceTypeResolver.resolve(eq(request), any())).thenReturn("web");

        interceptor.preHandle(request, response, new Object());

        verify(recorder).recordAsync("user-1", "web");
    }

    @Test
    @DisplayName("설정이 꺼져 있으면 아무것도 기록하지 않는다")
    void disabled() {
        properties.setEnabled(false);
        authenticateAs("user-1");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        verify(recorder, never()).recordAsync(anyString(), any());
    }

    @Test
    @DisplayName("인증 정보가 없으면 기록하지 않는다")
    void noAuthentication() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        verify(recorder, never()).recordAsync(anyString(), any());
    }

    @Test
    @DisplayName("비로그인(anonymousUser) 요청은 principal 이 문자열이어도 기록하지 않는다")
    void anonymous() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new AnonymousAuthenticationToken(
                                "key",
                                "anonymousUser",
                                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        verify(recorder, never()).recordAsync(anyString(), any());
    }

    @Test
    @DisplayName("principal 이 문자열이 아니거나 비어 있으면 기록하지 않는다")
    void principalNotUserId() {
        authenticateAs(new Object());
        interceptor.preHandle(request, response, new Object());

        authenticateAs("  ");
        interceptor.preHandle(request, response, new Object());

        verify(recorder, never()).recordAsync(anyString(), any());
    }

    @Test
    @DisplayName("isAuthenticated 가 false 인 토큰은 기록하지 않는다")
    void notAuthenticatedToken() {
        // 2-arg 생성자는 authenticated=false
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("user-1", null));

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        verify(recorder, never()).recordAsync(anyString(), any());
    }

    @Test
    @DisplayName("제외 목록에 있는 유저(E2E 봇 등)는 기록하지 않는다")
    void excludedUser() {
        properties.setExcludedUserIds(Set.of("bot-1"));
        authenticateAs("bot-1");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        verify(recorder, never()).recordAsync(anyString(), any());
    }

    @Test
    @DisplayName("기록 중 예외가 나도 요청은 통과한다")
    void swallowsException() {
        authenticateAs("user-1");
        when(deviceTypeResolver.resolve(eq(request), any())).thenReturn("web");
        doThrow(new IllegalStateException("executor saturated"))
                .when(recorder)
                .recordAsync("user-1", "web");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("currentUserId: 인증 principal 문자열을 돌려준다")
    void currentUserId() {
        authenticateAs("user-9");
        assertThat(UserActivityInterceptor.currentUserId()).isEqualTo("user-9");

        SecurityContextHolder.clearContext();
        assertThat(UserActivityInterceptor.currentUserId()).isNull();
    }
}
