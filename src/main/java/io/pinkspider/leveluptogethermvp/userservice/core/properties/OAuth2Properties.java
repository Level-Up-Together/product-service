package io.pinkspider.leveluptogethermvp.userservice.core.properties;

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

    public boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        return allowedOrigins.stream()
            .anyMatch(allowed -> origin.equalsIgnoreCase(allowed));
    }
}
