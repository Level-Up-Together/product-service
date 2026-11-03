package io.pinkspider.global.config.datasource.flyway;

import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * LUT-595: DB 하나의 Flyway 마이그레이션. 각 {@code XxxDataSourceConfig} 가 {@code initMethod = "migrate"} 빈으로
 * 등록하고, 같은 DB 의 {@code EntityManagerFactory} 가 {@code @DependsOn} 으로 이 빈 뒤에 뜬다 — Flyway 가 스키마를 맞춘 뒤
 * Hibernate {@code validate} 가 돈다.
 *
 * <p>파일 위치는 {@code classpath:db/migration/<db>/}, 이름은 {@code
 * V<major>_<minor>_<patch>_<seq3>__<desc>.sql} (예 {@code V1_1_4_001__lut600_add_x.sql}) — 릴리스 번호가
 * 앞이라 번호가 계속 커진다. 한 번 적용한 파일은 고치지 않는다(checksum).
 *
 * <p>큰 데이터 보정(재산정·소급·백필)은 여기 두지 않는다 — 서버 기동 시점에 자동으로 돌기엔 위험하므로 SQL 레포 + 수동 적용 유지.
 */
@Slf4j
public class FlywayMigration {

    static final String LOCATION_PREFIX = "classpath:db/migration/";

    private final String database;
    private final boolean enabled;
    private final Supplier<Flyway> flywaySupplier;

    FlywayMigration(String database, boolean enabled, Supplier<Flyway> flywaySupplier) {
        this.database = database;
        this.enabled = enabled;
        this.flywaySupplier = flywaySupplier;
    }

    /** {@code database} 는 폴더 이름(user·mission·guild·chat·meta·feed·notification·gamification) */
    public static FlywayMigration forDatabase(
            String database, DataSource dataSource, FlywayMigrationProperties properties) {
        return new FlywayMigration(
                database,
                properties.isEnabled(),
                () -> configure(database, dataSource, properties).load());
    }

    static org.flywaydb.core.api.configuration.FluentConfiguration configure(
            String database, DataSource dataSource, FlywayMigrationProperties properties) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION_PREFIX + database)
                // 기존 DB: history 표가 없으면 baseline 행을 만들고, 그 이하 버전 파일은 건너뛴다
                .baselineOnMigrate(true)
                .baselineVersion(properties.getBaselineVersion())
                .baselineDescription("baseline (manual migrations up to this version)")
                // 적용된 파일의 checksum 이 바뀌었거나 로컬에 없는 적용분이 있으면 기동 실패 — 조용히 넘어가지 않는다
                .validateOnMigrate(true)
                .outOfOrder(false)
                // 폴더가 비어 있어도(아직 파일 없음) 실패하지 않는다
                .failOnMissingLocations(false);
    }

    /** 빈 initMethod. 비활성이면 아무것도 하지 않는다. */
    public void migrate() {
        if (!enabled) {
            log.info("Flyway 비활성 (app.flyway.enabled=false): db={}", database);
            return;
        }
        MigrateResult result = flywaySupplier.get().migrate();
        // 적용이 0건이면 targetSchemaVersion 이 null — 그때 현재 버전은 initialSchemaVersion
        String current =
                result.targetSchemaVersion != null
                        ? result.targetSchemaVersion
                        : result.initialSchemaVersion;
        log.info(
                "Flyway 완료: db={}, 적용 {}건, 현재 버전 {} (기동 전 {})",
                database,
                result.migrationsExecuted,
                current,
                result.initialSchemaVersion);
    }
}
