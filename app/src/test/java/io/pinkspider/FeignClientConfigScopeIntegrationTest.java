package io.pinkspider;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Client;
import feign.RequestInterceptor;
import feign.Retryer;
import io.pinkspider.global.feign.admin.AdminInternalApiKeyInterceptor;
import java.util.List;
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

    /**
     * LUT-648: admin-internal-client 전용 Client(local 에서만 인증서 검증을 끄는)와 Retryer(최대 3회)가 외부 호출 클라이언트로
     * 새면 카카오·애플·구글 호출의 TLS 검증이 꺼지고 멱등이 아닌 호출도 재시도된다.
     */
    @Test
    @DisplayName("LUT-648: admin-internal 전용 Client·Retryer 는 외부 호출 클라이언트에 적용되지 않는다")
    void adminInternalBeansDoNotLeakToExternalClients() {
        for (String name :
                List.of(
                        "google-translation-client",
                        "kakao-oauth2-client",
                        "kakao-user-api",
                        "kakao-admin-api",
                        "apple-auth-api")) {
            assertThat(feignClientFactory.getInstances(name, Client.class))
                    .as("%s 에 adminInternal Client 가 보이면 안 된다", name)
                    .doesNotContainKey("adminInternalFeignClient");
            assertThat(feignClientFactory.getInstances(name, Retryer.class))
                    .as("%s 에 adminInternal Retryer 가 보이면 안 된다", name)
                    .doesNotContainKey("adminInternalFeignRetryer");
        }
        // 자기 클라이언트에는 그대로 적용된다 (Retryer 는 프로필 무관, trust-all Client 는 local 전용이라 test 에서는 없다)
        assertThat(feignClientFactory.getInstances("admin-internal-client", Retryer.class))
                .containsKey("adminInternalFeignRetryer");
        assertThat(feignClientFactory.getInstances("admin-internal-client", Client.class))
                .doesNotContainKey("adminInternalFeignClient");
    }

    @Test
    @DisplayName("LUT-686: admin 을 부르는 클라이언트 4종에만 내부 키 인터셉터가 있다 (외부 호출 클라이언트에는 없다)")
    void adminClientsCarryInternalApiKeyInterceptor() {
        for (String name :
                List.of(
                        "admin-notice-client",
                        "admin-inquiry-client",
                        "admin-report-client",
                        "admin-internal-client")) {
            assertThat(feignClientFactory.getInstances(name, RequestInterceptor.class))
                    .as("%s 에 내부 키 인터셉터가 있어야 한다", name)
                    .containsKey("adminInternalApiKeyInterceptor")
                    .hasValueSatisfying(
                            new org.assertj.core.api.Condition<>(
                                    i -> i instanceof AdminInternalApiKeyInterceptor,
                                    "AdminInternalApiKeyInterceptor 인스턴스"));
        }
        for (String name :
                List.of(
                        "google-translation-client",
                        "kakao-oauth2-client",
                        "kakao-user-api",
                        "kakao-admin-api",
                        "apple-auth-api")) {
            assertThat(feignClientFactory.getInstances(name, RequestInterceptor.class))
                    .as("%s 에 내부 키 인터셉터가 보이면 안 된다(내부 키 유출)", name)
                    .doesNotContainKey("adminInternalApiKeyInterceptor");
        }
    }

    @Test
    @DisplayName("어드민 패스스루 클라이언트에는 각자의 Authorization 전달 인터셉터 + 내부 키 인터셉터(LUT-686)만 적용된다")
    void adminClientsKeepTheirOwnInterceptor() {
        assertThat(feignClientFactory.getInstances("admin-notice-client", RequestInterceptor.class))
                .containsOnlyKeys(
                        "adminAuthorizationInterceptor", "adminInternalApiKeyInterceptor");
        assertThat(
                        feignClientFactory.getInstances(
                                "admin-inquiry-client", RequestInterceptor.class))
                .containsOnlyKeys(
                        "adminAuthorizationInterceptor", "adminInternalApiKeyInterceptor");
        assertThat(feignClientFactory.getInstances("admin-report-client", RequestInterceptor.class))
                .containsOnlyKeys(
                        "reportAuthorizationInterceptor", "adminInternalApiKeyInterceptor");
    }
}
