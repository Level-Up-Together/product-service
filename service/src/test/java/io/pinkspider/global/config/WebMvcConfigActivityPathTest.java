package io.pinkspider.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.i18n.LocaleInterceptor;
import io.pinkspider.global.interceptor.JwtInterceptor;
import io.pinkspider.global.interceptor.MultipartInterceptor;
import io.pinkspider.global.resolver.CurrentUserArgumentResolver;
import io.pinkspider.leveluptogethermvp.statsservice.activity.api.UserActivityInterceptor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * LUT-598 활동(DAU) 수집 경로 — 실제 {@link WebMvcConfig} 가 등록한 매핑으로 판정한다. LUT-777: 홈 화면 위젯 요약({@code
 * /api/v1/widget/**})은 앱을 열지 않아도 30분마다 불리므로 활동으로 세지 않는다.
 */
@DisplayName("WebMvcConfig — 활동(DAU) 수집 경로")
class WebMvcConfigActivityPathTest {

    /** getInterceptors() 가 protected 라 열어 준다 */
    static class OpenRegistry extends InterceptorRegistry {
        List<Object> all() {
            return getInterceptors();
        }
    }

    @SuppressWarnings("unchecked")
    private static MappedInterceptor activityMapping() {
        UserActivityInterceptor activity = mock(UserActivityInterceptor.class);
        ObjectProvider<UserActivityInterceptor> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(activity);
        WebMvcConfig config =
                new WebMvcConfig(
                        mock(LocaleInterceptor.class),
                        mock(JwtInterceptor.class),
                        mock(MultipartInterceptor.class),
                        mock(CurrentUserArgumentResolver.class),
                        provider);
        OpenRegistry registry = new OpenRegistry();
        config.addInterceptors(registry);
        return registry.all().stream()
                .filter(MappedInterceptor.class::isInstance)
                .map(MappedInterceptor.class::cast)
                .filter(m -> m.getInterceptor() == activity)
                .findFirst()
                .orElseThrow();
    }

    @ParameterizedTest(name = "{0} → 활동 {1}")
    @CsvSource({
        "/api/v1/missions, true",
        "/api/v1/bff/home, true",
        "/jwt/reissue, true",
        "/api/v1/widget/summary, false",
        "/api/internal/kpi/daily, false",
        "/api/test/login, false",
        "/api/v1/webhooks/subscriptions/google, false",
        "/uploads/a.png, false"
    })
    void activityPaths(String path, boolean counted) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        ServletRequestPathUtils.parseAndCache(request);

        assertThat(activityMapping().matches(request)).isEqualTo(counted);
    }
}
