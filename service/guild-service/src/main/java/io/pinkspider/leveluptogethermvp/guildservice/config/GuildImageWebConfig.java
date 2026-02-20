package io.pinkspider.leveluptogethermvp.guildservice.config;

import io.pinkspider.leveluptogethermvp.guildservice.application.GuildImageProperties;
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
public class GuildImageWebConfig implements WebMvcConfigurer {

    private final GuildImageProperties guildImageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = new File(guildImageProperties.getPath()).getAbsolutePath();
        String urlPrefix = guildImageProperties.getUrlPrefix();
        String resourceLocation = "file:" + uploadPath + "/";
        log.info("길드 이미지 리소스 핸들러: {} -> {}", urlPrefix + "/**", resourceLocation);
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(resourceLocation);
    }
}
