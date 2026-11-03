package io.pinkspider.leveluptogethermvp.chatservice.config;

import io.pinkspider.leveluptogethermvp.chatservice.application.ChatImageProperties;
import java.io.File;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 로컬 저장 채팅 이미지 정적 서빙 (LUT-590). 없으면 dev 에서 /uploads/chats/** 가 404. */
@Slf4j
@Configuration
@Profile("!prod")
@RequiredArgsConstructor
public class ChatImageWebConfig implements WebMvcConfigurer {

    private final ChatImageProperties chatImageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = new File(chatImageProperties.getPath()).getAbsolutePath();
        String urlPrefix = chatImageProperties.getUrlPrefix();
        String resourceLocation = "file:" + uploadPath + "/";
        log.info("채팅 이미지 리소스 핸들러: {} -> {}", urlPrefix + "/**", resourceLocation);
        // UUID 파일명이라 콘텐츠 불변 — 공격적 캐시 허용 (LUT-406 관례)
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(resourceLocation)
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
