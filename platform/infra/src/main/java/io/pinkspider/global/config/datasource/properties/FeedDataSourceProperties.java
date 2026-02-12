package io.pinkspider.global.config.datasource.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@ConfigurationProperties(prefix = "spring.datasource.feed")
@Configuration
@Profile("!test")
@Getter
@Setter
public class FeedDataSourceProperties {
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
}
