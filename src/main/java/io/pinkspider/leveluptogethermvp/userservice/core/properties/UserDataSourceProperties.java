package io.pinkspider.leveluptogethermvp.userservice.core.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "spring.datasource.user")
@Configuration
@Getter
@Setter
public class UserDataSourceProperties {

    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
}
