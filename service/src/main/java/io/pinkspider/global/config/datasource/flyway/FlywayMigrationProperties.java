package io.pinkspider.global.config.datasource.flyway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LUT-595: Flyway 설정 (8개 DB 공통).
 *
 * <p>Spring Boot 기본 Flyway 자동설정({@code spring.flyway.*})은 DB 하나만 다루므로 끄고({@code
 * spring.flyway.enabled=false}), 이 설정으로 DB 마다 {@link FlywayMigration} 을 만든다.
 *
 * <ul>
 *   <li>{@code enabled=false}(기본): Flyway 객체를 만들지 않는 no-op — 테스트·local·(v1.1.4 전) prod
 *   <li>{@code baseline-version}: 이미 스키마가 있는 DB 를 "여기까지 적용됨" 으로 받아들이는 지점. 환경마다 다를 수 있어
 *       config-repository 에서 준다 (dev 1.1.3.7 — 1.1.3 V001~V007 수동 적용분)
 * </ul>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.flyway")
public class FlywayMigrationProperties {

    /** false 면 아무 DB 에도 Flyway 를 돌리지 않는다 */
    private boolean enabled = false;

    /** 기존 DB 의 baseline 버전 (flyway_schema_history 가 없을 때 이 버전으로 baseline 행을 만든다) */
    private String baselineVersion = "1.1.3.7";
}
