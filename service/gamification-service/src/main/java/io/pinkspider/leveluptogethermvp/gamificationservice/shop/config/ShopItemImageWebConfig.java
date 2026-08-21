package io.pinkspider.leveluptogethermvp.gamificationservice.shop.config;

import io.pinkspider.leveluptogethermvp.gamificationservice.shop.application.ShopItemImageProperties;
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
        // LUT-406: UUID 파일명이라 콘텐츠 불변 — 공격적 캐시 허용. Cache-Control 을 명시하면
        // Spring Security 기본 캐시 억제 헤더(no-store 등)는 붙지 않는다 (기설정 시 미개입).
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(resourceLocation)
                .setCacheControl(
                        CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
