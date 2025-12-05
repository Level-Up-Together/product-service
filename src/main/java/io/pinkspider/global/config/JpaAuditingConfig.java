package io.pinkspider.global.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
@ComponentScan({"io.pinkspider.global"})
public class JpaAuditingConfig {

}
