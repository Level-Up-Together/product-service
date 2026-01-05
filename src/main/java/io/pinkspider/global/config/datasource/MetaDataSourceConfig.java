package io.pinkspider.global.config.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.pinkspider.global.component.SshTunnel;
import io.pinkspider.leveluptogethermvp.userservice.core.properties.MetaDataSourceProperties;
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
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = {
        "io.pinkspider.leveluptogethermvp.metaservice",
        "io.pinkspider.leveluptogethermvp.profanity.infrastructure",
        "io.pinkspider.global.translation.repository"
    },
    entityManagerFactoryRef = "metaEntityManagerFactory",
    transactionManagerRef = "metaTransactionManager"
)
@Profile("!test")
@Slf4j
public class MetaDataSourceConfig {

    private final MetaDataSourceProperties properties;
    private final SshTunnel sshTunnel;

    public MetaDataSourceConfig(MetaDataSourceProperties properties, SshTunnel sshTunnel) {
        this.properties = properties;
        this.sshTunnel = sshTunnel;
    }

    @Bean(name = "metaDataSource")
    @DependsOn("sshTunnel")
    public DataSource metaDataSource() {
        HikariConfig cfg = new HikariConfig();

        String jdbcUrl = DataSourceUtils.replacePortInJdbcUrl(properties.getJdbcUrl(), sshTunnel.getActualLocalPort());
        log.info("Meta DataSource JDBC URL: {}", jdbcUrl);

        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(properties.getUsername());
        cfg.setPassword(properties.getPassword());
        cfg.setDriverClassName(properties.getDriverClassName());

        cfg.setInitializationFailTimeout(30000);  // 30초 (무한 대기 방지)
        cfg.setConnectionTimeout(15000);
        cfg.setValidationTimeout(5000);
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setMaxLifetime(1800000);             // 30분
        cfg.setIdleTimeout(600000);              // 10분
        cfg.setLeakDetectionThreshold(60000);   // 커넥션 누수 감지 1분

        return new HikariDataSource(cfg);
    }

    @Bean(name = "metaEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean metaEntityManagerFactory(
        @Qualifier("metaDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan(
            "io.pinkspider.leveluptogethermvp.metaservice",
            "io.pinkspider.leveluptogethermvp.profanity.domain.entity",
            "io.pinkspider.global.translation.entity"
        );
        em.setPersistenceUnitName("meta");
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        em.setJpaProperties(jpaProperties());
        return em;
    }

    @Bean(name = "metaTransactionManager")
    public PlatformTransactionManager metaTransactionManager(
        @Qualifier("metaEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
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
