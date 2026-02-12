package io.pinkspider.leveluptogethermvp.userservice.mypage.config;

import io.pinkspider.leveluptogethermvp.userservice.mypage.application.ProfileImageProperties;
import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ProfileImageWebConfig implements WebMvcConfigurer {

    private final ProfileImageProperties profileImageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = new File(profileImageProperties.getPath()).getAbsolutePath();
        String urlPrefix = profileImageProperties.getUrlPrefix();
        String resourceLocation = "file:" + uploadPath + "/";
        log.info("프로필 이미지 리소스 핸들러: {} -> {}", urlPrefix + "/**", resourceLocation);
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(resourceLocation);
    }
}
