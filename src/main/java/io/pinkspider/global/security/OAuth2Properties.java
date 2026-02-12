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
        return allowedOrigins.stream()
            .anyMatch(allowed -> origin.equalsIgnoreCase(allowed));
    }

    @Data
    public static class KakaoWebhook {
        private String adminKey;
        private String restApiKey;
        private String appId;
    }
}
