package io.pinkspider.leveluptogethermvp.config;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

@Configuration
@Profile("test")
public class TestDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.user")
    public DataSource userDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.mission")
    public DataSource missionDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.guild")
    public DataSource guildDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.meta")
    public DataSource metaDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
        @Qualifier("userDataSource") DataSource dataSource,
        EntityManagerFactoryBuilder builder) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");

        return builder
            .dataSource(dataSource)
            .packages("io.pinkspider.leveluptogethermvp")
            .persistenceUnit("test")
            .properties(properties)
            .build();
    }
}
