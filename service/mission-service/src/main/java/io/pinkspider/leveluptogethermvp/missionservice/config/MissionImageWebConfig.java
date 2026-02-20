package io.pinkspider.leveluptogethermvp.missionservice.config;

import io.pinkspider.leveluptogethermvp.missionservice.application.MissionImageProperties;
import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
@Profile("!prod")
@RequiredArgsConstructor
public class MissionImageWebConfig implements WebMvcConfigurer {

    private final MissionImageProperties missionImageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = new File(missionImageProperties.getPath()).getAbsolutePath();
        String urlPrefix = missionImageProperties.getUrlPrefix();
        String resourceLocation = "file:" + uploadPath + "/";
        log.info("미션 이미지 리소스 핸들러: {} -> {}", urlPrefix + "/**", resourceLocation);
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(resourceLocation);
    }
}
