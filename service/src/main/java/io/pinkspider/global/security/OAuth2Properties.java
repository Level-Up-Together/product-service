package io.pinkspider.global.security;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.oauth2")
@Data
public class OAuth2Properties {

    private String googleTokenUrl;
    private String kakaoTokenUrl;
    private List<String> allowedOrigins = new ArrayList<>();
    private KakaoWebhook kakaoWebhook = new KakaoWebhook();

    public boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        return allowedOrigins.stream().anyMatch(allowed -> origin.equalsIgnoreCase(allowed));
    }

    @Data
    public static class KakaoWebhook {
        private String adminKey;
        private String restApiKey;
        private String appId;
    }

    /**
     * LUT-476: Sign in with Apple Server-to-Server Notification 수신 설정. audiences 는 알림 JWT 의 aud 허용
     * 목록 (앱 번들 ID·서비스 ID) — 비어 있으면 검증 생략 (kakaoWebhook.appId 의 null-스킵 관례와 동일).
     */
    private AppleWebhook appleWebhook = new AppleWebhook();

    @Data
    public static class AppleWebhook {
        private List<String> audiences = new ArrayList<>();
    }
}
