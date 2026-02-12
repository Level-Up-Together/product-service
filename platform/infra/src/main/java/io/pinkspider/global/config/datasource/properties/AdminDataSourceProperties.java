package io.pinkspider.global.config.datasource.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "spring.datasource.admin")
@Configuration
@Getter
@Setter
public class AdminDataSourceProperties {
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
}
