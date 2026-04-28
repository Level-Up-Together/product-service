package io.pinkspider.leveluptogethermvp.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.pinkspider.global.config.ShedLockConfig;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@DisplayName("ShedLockConfig 단위 테스트")
class ShedLockConfigTest {

    @Test
    @DisplayName("LockProvider Bean이 RedisLockProvider 타입으로 생성된다")
    void lockProvider_isRedisBased() {
        ShedLockConfig config = new ShedLockConfig();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        LockProvider provider = config.lockProvider(connectionFactory);

        assertThat(provider).isInstanceOf(RedisLockProvider.class);
    }

    @Test
    @DisplayName("@EnableSchedulerLock의 defaultLockAtMostFor가 PT10M으로 설정되어 있다")
    void enableSchedulerLock_hasDefaultLockAtMostFor() {
        EnableSchedulerLock annotation = ShedLockConfig.class.getAnnotation(EnableSchedulerLock.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.defaultLockAtMostFor()).isEqualTo("PT10M");
    }

    @Test
    @DisplayName("@Profile이 test/unit-test 환경을 제외하도록 설정되어 있다")
    void profile_excludesTestEnvironments() {
        Profile profile = ShedLockConfig.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value())
            .as("테스트 환경에서는 ShedLockConfig가 활성화되지 않아야 합니다 (Redis 연결 의존성 회피)")
            .anyMatch(p -> p.contains("!test"));
    }
}
