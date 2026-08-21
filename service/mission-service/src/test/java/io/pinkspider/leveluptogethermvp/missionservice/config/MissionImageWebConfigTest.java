package io.pinkspider.leveluptogethermvp.missionservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.missionservice.application.MissionImageProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

class MissionImageWebConfigTest {

    // LUT-406: /uploads 정적 이미지가 Spring Security 기본 캐시 억제 헤더(no-store)로
    // 서빙되지 않도록, 리소스 핸들러에 immutable 캐시 헤더를 명시했는지 검증한다.
    // (Cache-Control 이 설정된 응답에는 Security 가 캐시 헤더를 덮지 않는다)
    @Test
    @DisplayName("업로드 리소스 핸들러에 public/immutable 1년 캐시 헤더를 설정한다")
    @SuppressWarnings("unchecked")
    void addResourceHandlers_setsImmutableCacheControl() {
        MissionImageWebConfig config = new MissionImageWebConfig(new MissionImageProperties());
        ResourceHandlerRegistry registry =
                new ResourceHandlerRegistry(new StaticWebApplicationContext(), new MockServletContext());

        config.addResourceHandlers(registry);

        List<ResourceHandlerRegistration> registrations =
                (List<ResourceHandlerRegistration>) ReflectionTestUtils.getField(registry, "registrations");
        assertThat(registrations).hasSize(1);

        CacheControl cacheControl =
                (CacheControl) ReflectionTestUtils.getField(registrations.get(0), "cacheControl");
        assertThat(cacheControl).isNotNull();
        assertThat(cacheControl.getHeaderValue())
                .contains("max-age=31536000")
                .contains("public")
                .contains("immutable");
    }
}
