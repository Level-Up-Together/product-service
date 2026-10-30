package io.pinkspider.global.feign.admin;

import static org.assertj.core.api.Assertions.assertThat;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.pinkspider.leveluptogethermvp.noticeservice.core.config.AdminFeignConfig;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.config.ReportFeignConfig;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AdminInternalApiKeyInterceptor (LUT-686)")
class AdminInternalApiKeyInterceptorTest {

    private static final String HEADER = "X-Internal-Api-Key";

    @Test
    @DisplayName("키가 있으면 X-Internal-Api-Key 헤더를 싣는다")
    void addsHeaderWhenConfigured() {
        RequestTemplate template = new RequestTemplate();

        new AdminInternalApiKeyInterceptor("k3y").apply(template);

        assertThat(template.headers().get(HEADER)).containsExactly("k3y");
    }

    @ParameterizedTest(name = "키 [{0}] 이면 헤더를 싣지 않는다 (local fail-open)")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void noHeaderWhenBlank(String key) {
        RequestTemplate template = new RequestTemplate();
        AdminInternalApiKeyInterceptor interceptor = new AdminInternalApiKeyInterceptor(key);

        interceptor.apply(template);

        assertThat(interceptor.isConfigured()).isFalse();
        assertThat(template.headers()).doesNotContainKey(HEADER);
    }

    static Stream<Arguments> adminFeignConfigs() {
        return Stream.of(
                Arguments.of(
                        "AdminFeignConfig(공지·문의)",
                        (Function<String, RequestInterceptor>)
                                key -> new AdminFeignConfig().adminInternalApiKeyInterceptor(key)),
                Arguments.of(
                        "ReportFeignConfig(신고)",
                        (Function<String, RequestInterceptor>)
                                key -> new ReportFeignConfig().adminInternalApiKeyInterceptor(key)),
                Arguments.of(
                        "AdminInternalFeignConfig(내부 API)",
                        (Function<String, RequestInterceptor>)
                                key ->
                                        new AdminInternalFeignConfig()
                                                .adminInternalApiKeyInterceptor(key)));
    }

    @ParameterizedTest(name = "{0} 은 키 인터셉터를 등록한다")
    @MethodSource("adminFeignConfigs")
    @DisplayName("admin 을 부르는 Feign 설정 3개가 모두 키 헤더를 싣는다")
    void everyAdminFeignConfigSendsKey(String name, Function<String, RequestInterceptor> factory) {
        RequestTemplate template = new RequestTemplate();

        factory.apply("k3y").apply(template);

        assertThat(template.headers().get(HEADER)).as(name).containsExactly("k3y");
    }
}
