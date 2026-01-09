package io.pinkspider.leveluptogethermvp.config;

import jakarta.persistence.EntityManagerFactory;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@Profile("test")
@EnableTransactionManagement
public class TestDataSourceConfig {

    private Properties jpaProperties() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.show_sql", "true");
        return properties;
    }

    // ========== User DataSource ==========
    @Configuration
    @Profile("test")
    @EnableJpaRepositories(
        basePackages = "io.pinkspider.leveluptogethermvp.userservice",
        entityManagerFactoryRef = "userEntityManagerFactory",
        transactionManagerRef = "userTransactionManager"
    )
    static class TestUserDataSourceConfig {

        @Bean
        @Primary
        @ConfigurationProperties("spring.datasource.user")
        public DataSource userDataSource() {
            return DataSourceBuilder.create().build();
        }

        @Bean(name = "userEntityManagerFactory")
        @Primary
        public LocalContainerEntityManagerFactoryBean userEntityManagerFactory(
            @Qualifier("userDataSource") DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
            em.setDataSource(dataSource);
            em.setPackagesToScan("io.pinkspider.leveluptogethermvp.userservice");
            em.setPersistenceUnitName("user");
            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            em.setJpaVendorAdapter(vendorAdapter);
            em.setJpaProperties(jpaProperties());
            return em;
        }

        @Bean(name = "userTransactionManager")
        @Primary
        public PlatformTransactionManager userTransactionManager(
            @Qualifier("userEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        private Properties jpaProperties() {
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            properties.setProperty("hibernate.format_sql", "true");
            properties.setProperty("hibernate.show_sql", "true");
            return properties;
        }
    }

    // ========== Mission DataSource ==========
    @Configuration
    @Profile("test")
    @EnableJpaRepositories(
        basePackages = "io.pinkspider.leveluptogethermvp.missionservice",
        entityManagerFactoryRef = "missionEntityManagerFactory",
        transactionManagerRef = "missionTransactionManager"
    )
    static class TestMissionDataSourceConfig {

        @Bean
        @ConfigurationProperties("spring.datasource.mission")
        public DataSource missionDataSource() {
            return DataSourceBuilder.create().build();
        }

        @Bean(name = "missionEntityManagerFactory")
        public LocalContainerEntityManagerFactoryBean missionEntityManagerFactory(
            @Qualifier("missionDataSource") DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
            em.setDataSource(dataSource);
            em.setPackagesToScan("io.pinkspider.leveluptogethermvp.missionservice");
            em.setPersistenceUnitName("mission");
            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            em.setJpaVendorAdapter(vendorAdapter);
            em.setJpaProperties(jpaProperties());
            return em;
        }

        @Bean(name = "missionTransactionManager")
        public PlatformTransactionManager missionTransactionManager(
            @Qualifier("missionEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        private Properties jpaProperties() {
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            properties.setProperty("hibernate.format_sql", "true");
            properties.setProperty("hibernate.show_sql", "true");
            return properties;
        }
    }

    // ========== Guild DataSource ==========
    @Configuration
    @Profile("test")
    @EnableJpaRepositories(
        basePackages = "io.pinkspider.leveluptogethermvp.guildservice",
        entityManagerFactoryRef = "guildEntityManagerFactory",
        transactionManagerRef = "guildTransactionManager"
    )
    static class TestGuildDataSourceConfig {

        @Bean
        @ConfigurationProperties("spring.datasource.guild")
        public DataSource guildDataSource() {
            return DataSourceBuilder.create().build();
        }

        @Bean(name = "guildEntityManagerFactory")
        public LocalContainerEntityManagerFactoryBean guildEntityManagerFactory(
            @Qualifier("guildDataSource") DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
            em.setDataSource(dataSource);
            em.setPackagesToScan("io.pinkspider.leveluptogethermvp.guildservice");
            em.setPersistenceUnitName("guild");
            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            em.setJpaVendorAdapter(vendorAdapter);
            em.setJpaProperties(jpaProperties());
            return em;
        }

        @Bean(name = "guildTransactionManager")
        public PlatformTransactionManager guildTransactionManager(
            @Qualifier("guildEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        private Properties jpaProperties() {
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            properties.setProperty("hibernate.format_sql", "true");
            properties.setProperty("hibernate.show_sql", "true");
            return properties;
        }
    }

    // ========== Meta DataSource ==========
    @Configuration
    @Profile("test")
    @EnableJpaRepositories(
        basePackages = {
            "io.pinkspider.leveluptogethermvp.metaservice",
            "io.pinkspider.leveluptogethermvp.profanity.infrastructure",
            "io.pinkspider.global.translation.repository"
        },
        entityManagerFactoryRef = "metaEntityManagerFactory",
        transactionManagerRef = "metaTransactionManager"
    )
    static class TestMetaDataSourceConfig {

        @Bean
        @ConfigurationProperties("spring.datasource.meta")
        public DataSource metaDataSource() {
            return DataSourceBuilder.create().build();
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
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            properties.setProperty("hibernate.format_sql", "true");
            properties.setProperty("hibernate.show_sql", "true");
            return properties;
        }
    }

    // ========== Saga DataSource ==========
    @Configuration
    @Profile("test")
    @EnableJpaRepositories(
        basePackages = "io.pinkspider.global.saga.persistence",
        entityManagerFactoryRef = "sagaEntityManagerFactory",
        transactionManagerRef = "sagaTransactionManager"
    )
    static class TestSagaDataSourceConfig {

        @Bean
        @ConfigurationProperties("spring.datasource.saga")
        public DataSource sagaDataSource() {
            return DataSourceBuilder.create().build();
        }

        @Bean(name = "sagaEntityManagerFactory")
        public LocalContainerEntityManagerFactoryBean sagaEntityManagerFactory(
            @Qualifier("sagaDataSource") DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
            em.setDataSource(dataSource);
            em.setPackagesToScan("io.pinkspider.global.saga.persistence");
            em.setPersistenceUnitName("saga");
            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            em.setJpaVendorAdapter(vendorAdapter);
            em.setJpaProperties(jpaProperties());
            return em;
        }

        @Bean(name = "sagaTransactionManager")
        public PlatformTransactionManager sagaTransactionManager(
            @Qualifier("sagaEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        private Properties jpaProperties() {
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            properties.setProperty("hibernate.format_sql", "true");
            properties.setProperty("hibernate.show_sql", "true");
            return properties;
        }
    }

    // ========== Notification DataSource ==========
    @Configuration
    @Profile("test")
    @EnableJpaRepositories(
        basePackages = "io.pinkspider.leveluptogethermvp.notificationservice",
        entityManagerFactoryRef = "notificationEntityManagerFactory",
        transactionManagerRef = "notificationTransactionManager"
    )
    static class TestNotificationDataSourceConfig {

        @Bean
        @ConfigurationProperties("spring.datasource.notification")
        public DataSource notificationDataSource() {
            return DataSourceBuilder.create().build();
        }

        @Bean(name = "notificationEntityManagerFactory")
        public LocalContainerEntityManagerFactoryBean notificationEntityManagerFactory(
            @Qualifier("notificationDataSource") DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
            em.setDataSource(dataSource);
            em.setPackagesToScan("io.pinkspider.leveluptogethermvp.notificationservice");
            em.setPersistenceUnitName("notification");
            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            em.setJpaVendorAdapter(vendorAdapter);
            em.setJpaProperties(jpaProperties());
            return em;
        }

        @Bean(name = "notificationTransactionManager")
        public PlatformTransactionManager notificationTransactionManager(
            @Qualifier("notificationEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        private Properties jpaProperties() {
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            properties.setProperty("hibernate.format_sql", "true");
            properties.setProperty("hibernate.show_sql", "true");
            return properties;
        }
    }

    // ========== Admin DataSource ==========
    @Configuration
    @Profile("test")
    @EnableJpaRepositories(
        basePackages = "io.pinkspider.leveluptogethermvp.adminservice",
        entityManagerFactoryRef = "adminEntityManagerFactory",
        transactionManagerRef = "adminTransactionManager"
    )
    static class TestAdminDataSourceConfig {

        @Bean
        @ConfigurationProperties("spring.datasource.admin")
        public DataSource adminDataSource() {
            return DataSourceBuilder.create().build();
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
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            properties.setProperty("hibernate.format_sql", "true");
            properties.setProperty("hibernate.show_sql", "true");
            return properties;
        }
    }

    // ========== Feed DataSource ==========
    @Configuration
    @Profile("test")
    @EnableJpaRepositories(
        basePackages = "io.pinkspider.leveluptogethermvp.feedservice",
        entityManagerFactoryRef = "feedEntityManagerFactory",
        transactionManagerRef = "feedTransactionManager"
    )
    static class TestFeedDataSourceConfig {

        @Bean
        @ConfigurationProperties("spring.datasource.feed")
        public DataSource feedDataSource() {
            return DataSourceBuilder.create().build();
        }

        @Bean(name = "feedEntityManagerFactory")
        public LocalContainerEntityManagerFactoryBean feedEntityManagerFactory(
            @Qualifier("feedDataSource") DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
            em.setDataSource(dataSource);
            em.setPackagesToScan("io.pinkspider.leveluptogethermvp.feedservice");
            em.setPersistenceUnitName("feed");
            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            em.setJpaVendorAdapter(vendorAdapter);
            em.setJpaProperties(jpaProperties());
            return em;
        }

        @Bean(name = "feedTransactionManager")
        public PlatformTransactionManager feedTransactionManager(
            @Qualifier("feedEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        private Properties jpaProperties() {
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            properties.setProperty("hibernate.format_sql", "true");
            properties.setProperty("hibernate.show_sql", "true");
            return properties;
        }
    }

    // ========== Gamification DataSource ==========
    @Configuration
    @Profile("test")
    @EnableJpaRepositories(
        basePackages = "io.pinkspider.leveluptogethermvp.gamificationservice",
        entityManagerFactoryRef = "gamificationEntityManagerFactory",
        transactionManagerRef = "gamificationTransactionManager"
    )
    static class TestGamificationDataSourceConfig {

        @Bean
        @ConfigurationProperties("spring.datasource.gamification")
        public DataSource gamificationDataSource() {
            return DataSourceBuilder.create().build();
        }

        @Bean(name = "gamificationEntityManagerFactory")
        public LocalContainerEntityManagerFactoryBean gamificationEntityManagerFactory(
            @Qualifier("gamificationDataSource") DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
            em.setDataSource(dataSource);
            em.setPackagesToScan("io.pinkspider.leveluptogethermvp.gamificationservice");
            em.setPersistenceUnitName("gamification");
            HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
            em.setJpaVendorAdapter(vendorAdapter);
            em.setJpaProperties(jpaProperties());
            return em;
        }

        @Bean(name = "gamificationTransactionManager")
        public PlatformTransactionManager gamificationTransactionManager(
            @Qualifier("gamificationEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        private Properties jpaProperties() {
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            properties.setProperty("hibernate.format_sql", "true");
            properties.setProperty("hibernate.show_sql", "true");
            return properties;
        }
    }

}
