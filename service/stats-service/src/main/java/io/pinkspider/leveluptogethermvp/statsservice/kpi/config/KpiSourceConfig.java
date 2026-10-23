package io.pinkspider.leveluptogethermvp.statsservice.kpi.config;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.GamificationSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.MissionSourceReader;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.infrastructure.source.UserSourceReader;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * LUT-601: KPI 원천 리더 조립 — 각 서비스 DB 의 기존 DataSource 빈에 읽기 전용 JdbcTemplate 을 붙인다. 다른 서비스의
 * Repository/Service 는 import 하지 않는다(경계 규칙). 테스트 프로필에서는 TestDataSourceConfig 의 H2 DataSource 가 같은
 * 이름으로 주입된다.
 */
@Configuration
public class KpiSourceConfig {

    @Bean
    public UserSourceReader userSourceReader(@Qualifier("userDataSource") DataSource dataSource) {
        return new UserSourceReader(new NamedParameterJdbcTemplate(dataSource));
    }

    @Bean
    public GamificationSourceReader gamificationSourceReader(
            @Qualifier("gamificationDataSource") DataSource dataSource) {
        return new GamificationSourceReader(new NamedParameterJdbcTemplate(dataSource));
    }

    @Bean
    public MissionSourceReader missionSourceReader(
            @Qualifier("missionDataSource") DataSource dataSource) {
        return new MissionSourceReader(new NamedParameterJdbcTemplate(dataSource));
    }
}
