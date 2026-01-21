package io.pinkspider.leveluptogethermvp.supportservice.report.core.config;

import feign.Client;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * 신고 상태 확인용 FeignClient 설정
 * - 짧은 타임아웃 (연결 1초, 읽기 2초)
 * - 재시도 없음 (실패 시 빠르게 fallback)
 */
@Configuration
public class ReportFeignConfig {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Bean
    public Logger.Level reportFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * 재시도 없음 - 실패 시 바로 반환
     */
    @Bean
    public Retryer reportFeignRetryer() {
        return Retryer.NEVER_RETRY;
    }

    /**
     * 짧은 타임아웃 설정
     * - Connection timeout: 1초
     * - Read timeout: 2초
     */
    @Bean
    public Request.Options reportFeignRequestOptions() {
        return new Request.Options(
            1, TimeUnit.SECONDS,  // Connection timeout
            2, TimeUnit.SECONDS,  // Read timeout
            true                   // Follow redirects
        );
    }

    @Bean
    public RequestInterceptor reportAuthorizationInterceptor() {
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

    @Bean
    public Client reportFeignClient() {
        try {
            TrustManager[] trustManagers = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new java.security.SecureRandom());

            return new Client.Default(
                sslContext.getSocketFactory(),
                (hostname, session) -> true
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Feign client with SSL bypass", e);
        }
    }
}
