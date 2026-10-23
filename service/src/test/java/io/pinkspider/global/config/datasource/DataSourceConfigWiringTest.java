package io.pinkspider.global.config.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

/**
 * LUT-595 회귀 가드. DataSource 설정은 {@code @Profile("!test")} 라 컨텍스트 테스트가 잡지 못한다 — 2026-10-23 dev 배포에서
 * {@code userEntityManagerFactory} 의 {@code @Primary} 가 Flyway 빈 삽입 중 떨어져 나가 "expected single
 * matching bean but found 8" 로 기동에 실패했다. 어노테이션 배선을 리플렉션으로 고정한다.
 */
class DataSourceConfigWiringTest {

    private static final List<Class<?>> CONFIGS =
            List.of(
                    UserDataSourceConfig.class,
                    MissionDataSourceConfig.class,
                    GuildDataSourceConfig.class,
                    ChatDataSourceConfig.class,
                    MetaDataSourceConfig.class,
                    FeedDataSourceConfig.class,
                    NotificationDataSourceConfig.class,
                    GamificationDataSourceConfig.class);

    private static String db(Class<?> config) {
        String name = config.getSimpleName().replace("DataSourceConfig", "");
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static Method bean(Class<?> config, String beanName) {
        for (Method m : config.getDeclaredMethods()) {
            Bean b = m.getAnnotation(Bean.class);
            if (b != null && List.of(b.name()).contains(beanName)) {
                return m;
            }
        }
        throw new AssertionError(config.getSimpleName() + " 에 @Bean(name=\"" + beanName + "\") 없음");
    }

    @Test
    @DisplayName(
            "user 는 DataSource·EntityManagerFactory·TransactionManager 가 @Primary 이고, Flyway 빈은"
                    + " 아니다")
    void userBeansArePrimary() {
        assertThat(
                        bean(UserDataSourceConfig.class, "userDataSource")
                                .isAnnotationPresent(Primary.class))
                .isTrue();
        assertThat(
                        bean(UserDataSourceConfig.class, "userEntityManagerFactory")
                                .isAnnotationPresent(Primary.class))
                .as("userEntityManagerFactory @Primary — 없으면 EMF 8개가 모호해 기동 실패")
                .isTrue();
        assertThat(
                        bean(UserDataSourceConfig.class, "userTransactionManager")
                                .isAnnotationPresent(Primary.class))
                .isTrue();
        assertThat(
                        bean(UserDataSourceConfig.class, "userFlywayMigration")
                                .isAnnotationPresent(Primary.class))
                .isFalse();
    }

    @Test
    @DisplayName("8개 설정 모두: xxxFlywayMigration 빈이 있고, xxxEntityManagerFactory 가 그 빈에 @DependsOn 한다")
    void everyEntityManagerFactoryDependsOnFlyway() {
        for (Class<?> config : CONFIGS) {
            String db = db(config);
            Method flyway = bean(config, db + "FlywayMigration");
            assertThat(flyway.getAnnotation(Bean.class).initMethod()).isEqualTo("migrate");

            Method emf = bean(config, db + "EntityManagerFactory");
            DependsOn dependsOn = emf.getAnnotation(DependsOn.class);
            assertThat(dependsOn).as("%s @DependsOn", emf.getName()).isNotNull();
            assertThat(dependsOn.value()).contains(db + "FlywayMigration");
        }
    }

    @Test
    @DisplayName("user 외 7개 설정에는 @Primary 가 하나도 없다 (primary 는 user 하나뿐)")
    void onlyUserHasPrimary() {
        for (Class<?> config : CONFIGS) {
            if (config == UserDataSourceConfig.class) continue;
            for (Method m : config.getDeclaredMethods()) {
                assertThat(m.isAnnotationPresent(Primary.class))
                        .as("%s.%s 에 @Primary", config.getSimpleName(), m.getName())
                        .isFalse();
            }
        }
    }
}
