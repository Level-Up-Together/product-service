package io.pinkspider.leveluptogethermvp.towerservice.config;

import io.pinkspider.leveluptogethermvp.towerservice.application.image.TowerImageProperties;
import java.io.File;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * LUT-635: 로컬 저장 탑 층 이미지 정적 서빙 (prod 외). 없으면 dev 에서 {@code /uploads/tower/**} 가 404 → 마스크 검사·어드민
 * 미리보기 실패.
 */
@Slf4j
@Configuration
@Profile("!prod")
@RequiredArgsConstructor
public class TowerImageWebConfig implements WebMvcConfigurer {
    private final TowerImageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourceLocation = "file:" + new File(properties.getPath()).getAbsolutePath() + "/";
        String pattern = properties.getUrlPrefix() + "/**";
        log.info("탑 층 이미지 리소스 핸들러: {} -> {}", pattern, resourceLocation);
        registry.addResourceHandler(pattern)
                .addResourceLocations(resourceLocation)
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
