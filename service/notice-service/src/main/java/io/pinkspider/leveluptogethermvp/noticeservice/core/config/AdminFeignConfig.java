package io.pinkspider.leveluptogethermvp.noticeservice.core.config;

import feign.Client;
import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 클라이언트 전용 설정. {@code @FeignClient(configuration = ...)} 로만 참조되어 클라이언트별 자식 컨텍스트에 로드된다.
 *
 * <p>⚠ {@code @Configuration} 을 붙이지 말 것 — 컴포넌트 스캔으로 부모 컨텍스트에 올라가면 아래 {@code RequestInterceptor}(현재
 * 요청의 Authorization 전달)와 trust-all {@code Client} 가 Google 번역·카카오 등 모든 Feign 클라이언트에 전역 적용된다. 실제로
 * prod 에서 유저 JWT 가 Google Translation API 로 전달돼 401 이 났다.
 */
public class AdminFeignConfig {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Bean
    public Logger.Level adminFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Retryer adminFeignRetryer() {
        return new Retryer.Default(1000, 2000, 3);
    }

    /** 현재 요청의 Authorization 헤더를 Admin API 호출에 전달 */
    @Bean
    public RequestInterceptor adminAuthorizationInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String authHeader = attributes.getRequest().getHeader(AUTHORIZATION_HEADER);
                if (authHeader != null && !authHeader.isEmpty()) {
                    requestTemplate.header(AUTHORIZATION_HEADER, authHeader);
                }
            }
        };
    }

    /** 자체 서명 SSL 인증서를 허용하는 Feign Client 설정 로컬/개발 환경에서 HTTPS 호출을 위해 필요 */
    @Bean
    public Client feignClient() {
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
