package io.pinkspider.global.config.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.pinkspider.global.component.SshTunnel;
import io.pinkspider.global.config.datasource.flyway.FlywayMigration;
import io.pinkspider.global.config.datasource.flyway.FlywayMigrationProperties;
import io.pinkspider.global.config.datasource.properties.TowerDataSourceProperties;
import jakarta.persistence.EntityManagerFactory;
import java.util.Properties;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.lang.Nullable;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * LUT-629: 레벨업 탑 전용 {@code tower_db} (10번째 DB). 층·인스턴스·presence·채팅 로그·NPC·대기열이 한 도메인이라 서비스별 DB 분리
 * 규칙대로 신설 — 같은 RDS 인스턴스. 패턴은 다른 9개 {@code XxxDataSourceConfig} 와 동일 (LUT-595 Flyway → JPA validate
 * 순서 포함).
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "io.pinkspider.leveluptogethermvp.towerservice",
        entityManagerFactoryRef = "towerEntityManagerFactory",
        transactionManagerRef = "towerTransactionManager")
@Profile("!test & !push-test")
@Slf4j
public class TowerDataSourceConfig {

    private final TowerDataSourceProperties properties;
    private final SshTunnel sshTunnel;

    public TowerDataSourceConfig(
            TowerDataSourceProperties properties, @Nullable SshTunnel sshTunnel) {
        this.properties = properties;
        this.sshTunnel = sshTunnel;
    }

    @Bean(name = "towerDataSource")
    public DataSource towerDataSource() {
        HikariConfig cfg = new HikariConfig();

        String jdbcUrl =
                sshTunnel != null
                        ? DataSourceUtils.replacePortInJdbcUrl(
                                properties.getJdbcUrl(), sshTunnel.getActualLocalPort())
                        : properties.getJdbcUrl();
        log.info("Tower DataSource JDBC URL: {}", jdbcUrl);

        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(properties.getUsername());
        cfg.setPassword(properties.getPassword());
        cfg.setDriverClassName(properties.getDriverClassName());

        cfg.setInitializationFailTimeout(30000);
        cfg.setConnectionTimeout(15000);
        cfg.setValidationTimeout(5000);
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setMaxLifetime(1800000);
        cfg.setIdleTimeout(600000);
        cfg.setLeakDetectionThreshold(60000);

        return new HikariDataSource(cfg);
    }

    /** LUT-595: Flyway → JPA 순서 보장. {@code app.flyway.enabled=false} 면 no-op */
    @Bean(name = "towerFlywayMigration", initMethod = "migrate")
    public FlywayMigration towerFlywayMigration(
            @Qualifier("towerDataSource") DataSource dataSource,
            FlywayMigrationProperties flywayProperties) {
        return FlywayMigration.forDatabase("tower", dataSource, flywayProperties);
    }

    @DependsOn("towerFlywayMigration")
    @Bean(name = "towerEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean towerEntityManagerFactory(
            @Qualifier("towerDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("io.pinkspider.leveluptogethermvp.towerservice");
        em.setPersistenceUnitName("tower");
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        em.setJpaProperties(jpaProperties());
        return em;
    }

    @Bean(name = "towerTransactionManager")
    public PlatformTransactionManager towerTransactionManager(
            @Qualifier("towerEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    private Properties jpaProperties() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "validate");
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.show_sql", "false");
        return properties;
    }
}
