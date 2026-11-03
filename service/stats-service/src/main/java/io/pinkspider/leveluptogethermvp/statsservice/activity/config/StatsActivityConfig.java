package io.pinkspider.leveluptogethermvp.statsservice.activity.config;

import io.pinkspider.global.web.DeviceTypeResolver;
import io.pinkspider.leveluptogethermvp.statsservice.activity.api.UserActivityInterceptor;
import io.pinkspider.leveluptogethermvp.statsservice.activity.application.UserActivityRecorder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LUT-598: 활동 수집 빈 조립.
 *
 * <p>인터셉터를 {@code @Component} 가 아니라 {@code @Bean} 으로 두는 이유 — {@code @WebMvcTest} 슬라이스는 {@code
 * HandlerInterceptor} 컴포넌트를 전부 스캔하므로, {@code @Component} 면 Redis·Recorder 빈이 없는 컨트롤러 테스트 수백 개가 함께
 * 깨진다. {@code @Configuration} 은 슬라이스에 올라오지 않고, {@code WebMvcConfig} 는 {@code ObjectProvider} 로 있을
 * 때만 등록한다.
 */
@Configuration
@EnableConfigurationProperties(UserActivityProperties.class)
public class StatsActivityConfig {

    @Bean
    public UserActivityInterceptor userActivityInterceptor(
            UserActivityRecorder recorder,
            UserActivityProperties properties,
            DeviceTypeResolver deviceTypeResolver) {
        return new UserActivityInterceptor(recorder, properties, deviceTypeResolver);
    }
}
