package io.pinkspider.global.saga.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "spring.datasource.saga")
@Configuration
@Getter
@Setter
public class SagaDataSourceProperties {

    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
}
