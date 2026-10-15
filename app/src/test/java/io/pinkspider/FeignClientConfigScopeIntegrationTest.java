package io.pinkspider;

import static org.assertj.core.api.Assertions.assertThat;

import feign.RequestInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.test.context.ActiveProfiles;

/**
 * LUT-571 — Feign 클라이언트별 설정이 자기 클라이언트에만 적용되는지 실제 컨텍스트로 검증한다.
 *
 * <p>어드민 패스스루 설정({@code AdminFeignConfig}/{@code ReportFeignConfig})의 Authorization 전달 인터셉터가 전역으로
 * 새면 Google Translation 호출에 유저 JWT 가 실려 401 이 난다(prod 사고).
 */
@SpringBootTest
@ActiveProfiles("test")
class FeignClientConfigScopeIntegrationTest {

    @Autowired private FeignClientFactory feignClientFactory;

    @Test
    @DisplayName("Google Translation 클라이언트에는 Authorization 전달 인터셉터가 적용되지 않는다")
    void googleTranslationClientHasNoRequestInterceptor() {
        assertThat(
                        feignClientFactory.getInstances(
                                "google-translation-client", RequestInterceptor.class))
                .isEmpty();
    }

    @Test
    @DisplayName("어드민 패스스루 클라이언트에는 각자의 Authorization 전달 인터셉터가 적용된다")
    void adminClientsKeepTheirOwnInterceptor() {
        assertThat(feignClientFactory.getInstances("admin-notice-client", RequestInterceptor.class))
                .hasSize(1);
        assertThat(
                        feignClientFactory.getInstances(
                                "admin-inquiry-client", RequestInterceptor.class))
                .hasSize(1);
        assertThat(feignClientFactory.getInstances("admin-report-client", RequestInterceptor.class))
                .hasSize(1);
    }
}
