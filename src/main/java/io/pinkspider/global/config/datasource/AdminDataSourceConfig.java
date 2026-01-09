package io.pinkspider.global.config.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.pinkspider.global.component.SshTunnel;
import io.pinkspider.leveluptogethermvp.adminservice.core.properties.AdminDataSourceProperties;
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
    basePackages = "io.pinkspider.leveluptogethermvp.adminservice",
    entityManagerFactoryRef = "adminEntityManagerFactory",
    transactionManagerRef = "adminTransactionManager"
)
@Profile("!test")
@Slf4j
public class AdminDataSourceConfig {

    private final AdminDataSourceProperties properties;
    private final SshTunnel sshTunnel;

    public AdminDataSourceConfig(AdminDataSourceProperties properties, SshTunnel sshTunnel) {
        this.properties = properties;
        this.sshTunnel = sshTunnel;
    }

    @Bean(name = "adminDataSource")
    @DependsOn("sshTunnel")
    public DataSource adminDataSource() {
        HikariConfig cfg = new HikariConfig();

        String jdbcUrl = DataSourceUtils.replacePortInJdbcUrl(properties.getJdbcUrl(), sshTunnel.getActualLocalPort());
        log.info("Admin DataSource JDBC URL: {}", jdbcUrl);

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

    @Bean(name = "adminEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean adminEntityManagerFactory(
        @Qualifier("adminDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("io.pinkspider.leveluptogethermvp.adminservice");
        em.setPersistenceUnitName("admin");
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        em.setJpaProperties(jpaProperties());
        return em;
    }

    @Bean(name = "adminTransactionManager")
    public PlatformTransactionManager adminTransactionManager(
        @Qualifier("adminEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
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
