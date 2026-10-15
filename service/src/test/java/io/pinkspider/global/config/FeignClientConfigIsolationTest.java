package io.pinkspider.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.translation.GoogleTranslationConfig;
import io.pinkspider.leveluptogethermvp.noticeservice.core.config.AdminFeignConfig;
import io.pinkspider.leveluptogethermvp.supportservice.report.core.config.ReportFeignConfig;
import io.pinkspider.leveluptogethermvp.userservice.core.config.KakaoFeignConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 클라이언트 전용 설정 클래스가 컴포넌트 스캔 대상이 되지 않도록 지키는 회귀 가드.
 *
 * <p>{@code @FeignClient(configuration = X.class)} 의 X 에 {@code @Configuration} 이 붙으면 Spring 이 부모
 * 컨텍스트에도 등록해 그 안의 {@code RequestInterceptor}·{@code Client}·{@code Retryer} 가 모든 Feign 클라이언트에 전역
 * 적용된다. 어드민 패스스루 설정의 Authorization 전달 인터셉터가 Google Translation 호출에 섞여 prod 에서 401 이 났던 사고의 재발
 * 방지용이다.
 */
class FeignClientConfigIsolationTest {

    private static final List<Class<?>> FEIGN_CLIENT_CONFIGS =
            List.of(
                    AdminFeignConfig.class,
                    ReportFeignConfig.class,
                    KakaoFeignConfig.class,
                    GoogleTranslationConfig.class);

    @Test
    @DisplayName("Feign 클라이언트 전용 설정 클래스는 @Configuration 으로 컴포넌트 스캔되지 않는다")
    void feignClientConfigsAreNotComponentScanned() {
        for (Class<?> config : FEIGN_CLIENT_CONFIGS) {
            assertThat(config.isAnnotationPresent(Configuration.class))
                    .as("%s 는 @Configuration 이면 안 된다 (전역 Feign 빈 누출)", config.getSimpleName())
                    .isFalse();
        }
    }
}
