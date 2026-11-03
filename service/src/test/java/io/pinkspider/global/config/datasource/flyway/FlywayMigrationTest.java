package io.pinkspider.global.config.datasource.flyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LUT-595: DB 별 Flyway 래퍼 — enabled 분기, 구성값, 지연 생성 */
class FlywayMigrationTest {

    private FlywayMigrationProperties props(boolean enabled, String baseline) {
        FlywayMigrationProperties p = new FlywayMigrationProperties();
        p.setEnabled(enabled);
        p.setBaselineVersion(baseline);
        return p;
    }

    @Test
    @DisplayName("비활성이면 Flyway 를 만들지도, 돌리지도 않는다")
    void disabled_isNoop() {
        AtomicInteger created = new AtomicInteger();
        FlywayMigration migration =
                new FlywayMigration(
                        "user",
                        false,
                        () -> {
                            created.incrementAndGet();
                            return mock(Flyway.class);
                        });

        migration.migrate();

        assertThat(created).hasValue(0);
    }

    @Test
    @DisplayName("활성이면 Flyway.migrate() 를 호출한다")
    void enabled_migrates() {
        Flyway flyway = mock(Flyway.class);
        when(flyway.migrate()).thenReturn(mock(MigrateResult.class));
        FlywayMigration migration = new FlywayMigration("user", true, () -> flyway);

        migration.migrate();

        verify(flyway).migrate();
    }

    @Test
    @DisplayName("forDatabase: 비활성이면 DataSource 를 건드리지 않는다 (테스트·local·prod 안전)")
    void forDatabase_disabled_neverTouchesDataSource() {
        DataSource dataSource = mock(DataSource.class);

        FlywayMigration.forDatabase("user", dataSource, props(false, "1.1.3.7")).migrate();

        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("forDatabase: 활성이면 migrate 시점에 비로소 DB 에 연결한다 (지연 생성)")
    void forDatabase_enabled_connectsLazily() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("no db in unit test"));
        FlywayMigration migration =
                FlywayMigration.forDatabase("user", dataSource, props(true, "1.1.3.7"));

        assertThatThrownBy(migration::migrate).isInstanceOf(RuntimeException.class);

        verify(dataSource, atLeastOnce()).getConnection();
    }

    @Test
    @DisplayName("구성: DB 별 폴더, baseline(on-migrate + 버전), validate on, out-of-order off, 빈 폴더 허용")
    void configure_values() {
        DataSource dataSource = mock(DataSource.class);

        FluentConfiguration config =
                FlywayMigration.configure("gamification", dataSource, props(true, "1.1.3.7"));

        assertThat(config.getLocations()).hasSize(1);
        assertThat(config.getLocations()[0].getDescriptor())
                .isEqualTo("classpath:db/migration/gamification");
        assertThat(config.isBaselineOnMigrate()).isTrue();
        assertThat(config.getBaselineVersion().getVersion()).isEqualTo("1.1.3.7");
        assertThat(config.isValidateOnMigrate()).isTrue();
        assertThat(config.isOutOfOrder()).isFalse();
        assertThat(config.isFailOnMissingLocations()).isFalse();
        assertThat(config.getDataSource()).isSameAs(dataSource);
    }

    @Test
    @DisplayName("기본값: 비활성, baseline 1.1.3.7 (1.1.3 V007 까지 수동 적용)")
    void properties_defaults() {
        FlywayMigrationProperties p = new FlywayMigrationProperties();

        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getBaselineVersion()).isEqualTo("1.1.3.7");
    }
}
