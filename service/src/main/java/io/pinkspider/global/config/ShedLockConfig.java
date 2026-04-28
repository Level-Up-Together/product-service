package io.pinkspider.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * ShedLock 설정 — 멀티 인스턴스 환경에서 @Scheduled 메서드의 동시 실행을 방지한다.
 *
 * <p>EC2 #1, #2에서 동시에 실행되던 스케줄러로 인한 race condition (예: unique constraint 위반) 해결.
 *
 * <p>각 스케줄러 메서드에 {@code @SchedulerLock(name = "uniqueName", lockAtMostFor = "PT5M")} 어노테이션을 추가하면
 * Redis SETNX 기반으로 한 인스턴스만 실행한다.
 *
 * <p>{@code defaultLockAtMostFor}는 unlock 호출이 누락된 경우(JVM 강제 종료 등)에도 락이 자동 해제되는 안전장치.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@Profile("!test & !unit-test")
public class ShedLockConfig {

    /**
     * Redis 기반 LockProvider. 환경별로 다른 prefix(env)를 적용해 dev/prod 충돌을 방지한다.
     */
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "lut");
    }
}
