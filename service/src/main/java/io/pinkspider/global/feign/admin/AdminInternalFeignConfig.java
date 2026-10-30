package io.pinkspider.global.feign.admin;

import feign.Client;
import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * admin-internal-client 전용 설정 — {@code @FeignClient(configuration = ...)} 로만 참조한다.
 *
 * <p>LUT-648: 예전엔 {@code @Configuration} 이 붙어 부모 컨텍스트에 등록되는 바람에 아래 trust-all {@link Client}·{@link
 * Retryer} 가 자기 Client 가 없는 모든 Feign 클라이언트(카카오·애플·구글 번역)에 전역 적용됐다 — 외부 호출의 TLS 인증서·호스트 검증이 꺼지고, 멱등이
 * 아닌 호출(카카오 unlink 등)도 재시도됐다. {@code @Configuration} 금지 규칙은 LUT-571, 가드는
 * FeignClientConfigIsolationTest·FeignClientConfigScopeIntegrationTest.
 */
public class AdminInternalFeignConfig {

    @Bean
    public Logger.Level adminInternalFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Retryer adminInternalFeignRetryer() {
        return new Retryer.Default(1000, 2000, 3);
    }

    /** LUT-686: admin 의 product 전용 경로(문의·신고 확인·내부 API)는 공유 키를 요구한다 — product 내부 API 키와 같은 값. */
    @Bean
    public RequestInterceptor adminInternalApiKeyInterceptor(
            @Value("${app.security.internal-api.key:}") String internalApiKey) {
        return new AdminInternalApiKeyInterceptor(internalApiKey);
    }

    /**
     * 인증서·호스트 검증을 끈 Client — local 프로필 전용. dev(Sectigo)·prod(ACM, admin-api.level-up-together.com)는
     * 정식 인증서라 기본 Client 로 검증한다(LUT-648 확인 2026-10-30). local 의 admin(:8444)만 자체 서명일 수 있어 남긴다.
     */
    @Bean
    @Profile("local")
    public Client adminInternalFeignClient() {
        try {
            TrustManager[] trustManagers =
                    new TrustManager[] {
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(
                                    X509Certificate[] chain, String authType) {}

                            @Override
                            public void checkServerTrusted(
                                    X509Certificate[] chain, String authType) {}

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }
                    };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new java.security.SecureRandom());

            return new Client.Default(sslContext.getSocketFactory(), (hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Feign client with SSL bypass", e);
        }
    }
}
