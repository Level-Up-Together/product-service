package io.pinkspider.global.config.datasource.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** LUT-598: stats_db 접속 정보 — {@code spring.datasource.stats.*} (config-repository 환경별) */
@ConfigurationProperties(prefix = "spring.datasource.stats")
@Configuration
@Profile("!test")
@Getter
@Setter
public class StatsDataSourceProperties {
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
}
