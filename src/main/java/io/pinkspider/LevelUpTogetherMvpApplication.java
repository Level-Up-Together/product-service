package io.pinkspider;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@Slf4j
public class LevelUpTogetherMvpApplication {

    public static void main(String[] args) {
        SpringApplication.run(LevelUpTogetherMvpApplication.class, args);
    }

    @Bean
    @Profile("!test")
    CommandLineRunner whoAmI(DataSource ds) {
        return args -> {
            try (var c = ds.getConnection();
                var st = c.createStatement();
                var rs = st.executeQuery("""
                            select current_database() as db,
                                   current_user as usr,
                                   inet_server_addr()::text as srv,
                                   inet_server_port() as port,
                                   current_setting('search_path') as path
                    """)) {
                if (rs.next()) {
                    log.info("DB={} user={} server={}}:{} search_path={}}",
                        rs.getString("db"), rs.getString("usr"),
                        rs.getString("srv"), rs.getInt("port"),
                        rs.getString("path"));
                }
            }
        };
    }

//    @Bean
//    @Profile("!test")
//    CommandLineRunner checkConfig(@Value("${spring.datasource.url}") String url) {
//        return args -> {
//            log.info("Configured datasource URL: {}", url);
//        };
//    }
}
