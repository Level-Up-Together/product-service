package io.pinkspider.leveluptogethermvp.gamificationservice.shop.config;

import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ShopItemImageProperties;
import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 저장 상점 아이템 이미지 정적 서빙 (LUT-225)
 * 누락 시 dev에서 /uploads/shop-items/** 가 404 → 어드민 미리보기 미표시.
 */
@Slf4j
@Configuration
@Profile("!prod")
@RequiredArgsConstructor
public class ShopItemImageWebConfig implements WebMvcConfigurer {

    private final ShopItemImageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = new File(properties.getPath()).getAbsolutePath();
        String urlPrefix = properties.getUrlPrefix();
        String resourceLocation = "file:" + uploadPath + "/";
        log.info("상점 아이템 이미지 리소스 핸들러: {} -> {}", urlPrefix + "/**", resourceLocation);
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(resourceLocation);
    }
}
