package io.pinkspider.leveluptogethermvp.userservice.core.properties;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.jwt.refresh")
@Data
public class JwtProperties {

    private int renewalThresholdDays = 3;  // 기본값 3일
    private int maxLifetimeDays = 30;      // 기본값 30일

    public long getRenewalThresholdMillis() {
        return Duration.ofDays(renewalThresholdDays).toMillis();
    }

    public long getMaxLifetimeMillis() {
        return Duration.ofDays(maxLifetimeDays).toMillis();
    }
}
