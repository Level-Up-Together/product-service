package io.pinkspider.leveluptogethermvp.gamificationservice.event.config;

import io.pinkspider.leveluptogethermvp.gamificationservice.event.application.EventImageProperties;
import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EventImageWebConfig implements WebMvcConfigurer {

    private final EventImageProperties eventImageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = new File(eventImageProperties.getPath()).getAbsolutePath();
        String urlPrefix = eventImageProperties.getUrlPrefix();
        String resourceLocation = "file:" + uploadPath + "/";
        log.info("이벤트 이미지 리소스 핸들러: {} -> {}", urlPrefix + "/**", resourceLocation);
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(resourceLocation);
    }
}
