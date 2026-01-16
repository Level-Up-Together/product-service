package io.pinkspider.global.config;

import io.pinkspider.global.interceptor.JwtInterceptor;
import io.pinkspider.global.interceptor.MultipartInterceptor;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.application.EventImageProperties;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildImageProperties;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionImageProperties;
import io.pinkspider.leveluptogethermvp.userservice.core.resolver.CurrentUserArgumentResolver;
import io.pinkspider.leveluptogethermvp.userservice.mypage.application.ProfileImageProperties;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String[] JWT_EXCLUDE_PATTERNS = {
        // 정적 리소스 (이미지 업로드 등)
        "/uploads/**",
        "/favicon.ico",
        "/error"
    };
    private final JwtInterceptor jwtInterceptor;
    private final MultipartInterceptor multipartInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final ProfileImageProperties profileImageProperties;
    private final MissionImageProperties missionImageProperties;
    private final GuildImageProperties guildImageProperties;
    private final EventImageProperties eventImageProperties;

    @PostConstruct
    public void init() {
        String missionPath = missionImageProperties.getPath();
        String profilePath = profileImageProperties.getPath();
        String guildPath = guildImageProperties.getPath();
        String eventPath = eventImageProperties.getPath();
        File missionDir = new File(missionPath);
        File profileDir = new File(profilePath);
        File guildDir = new File(guildPath);
        File eventDir = new File(eventPath);

        log.info("=== 이미지 업로드 설정 ===");
        log.info("미션 이미지 경로: {} (절대경로: {}, 존재: {})",
            missionPath, missionDir.getAbsolutePath(), missionDir.exists());
        log.info("프로필 이미지 경로: {} (절대경로: {}, 존재: {})",
            profilePath, profileDir.getAbsolutePath(), profileDir.exists());
        log.info("길드 이미지 경로: {} (절대경로: {}, 존재: {})",
            guildPath, guildDir.getAbsolutePath(), guildDir.exists());
        log.info("이벤트 이미지 경로: {} (절대경로: {}, 존재: {})",
            eventPath, eventDir.getAbsolutePath(), eventDir.exists());
        log.info("미션 이미지 URL 접두어: {}", missionImageProperties.getUrlPrefix());
        log.info("프로필 이미지 URL 접두어: {}", profileImageProperties.getUrlPrefix());
        log.info("길드 이미지 URL 접두어: {}", guildImageProperties.getUrlPrefix());
        log.info("이벤트 이미지 URL 접두어: {}", eventImageProperties.getUrlPrefix());
        log.info("===========================");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 프로필 이미지 업로드 디렉토리 매핑 (더 구체적인 패턴 먼저 등록)
        String profileUploadPath = new File(profileImageProperties.getPath()).getAbsolutePath();
        String profileUrlPrefix = profileImageProperties.getUrlPrefix();
        String profileResourceLocation = "file:" + profileUploadPath + "/";
        log.info("프로필 이미지 리소스 핸들러: {} -> {}", profileUrlPrefix + "/**", profileResourceLocation);
        registry.addResourceHandler(profileUrlPrefix + "/**")
                .addResourceLocations(profileResourceLocation);

        // 미션 이미지 업로드 디렉토리 매핑
        String missionUploadPath = new File(missionImageProperties.getPath()).getAbsolutePath();
        String missionUrlPrefix = missionImageProperties.getUrlPrefix();
        String missionResourceLocation = "file:" + missionUploadPath + "/";
        log.info("미션 이미지 리소스 핸들러: {} -> {}", missionUrlPrefix + "/**", missionResourceLocation);
        registry.addResourceHandler(missionUrlPrefix + "/**")
                .addResourceLocations(missionResourceLocation);

        // 길드 이미지 업로드 디렉토리 매핑
        String guildUploadPath = new File(guildImageProperties.getPath()).getAbsolutePath();
        String guildUrlPrefix = guildImageProperties.getUrlPrefix();
        String guildResourceLocation = "file:" + guildUploadPath + "/";
        log.info("길드 이미지 리소스 핸들러: {} -> {}", guildUrlPrefix + "/**", guildResourceLocation);
        registry.addResourceHandler(guildUrlPrefix + "/**")
                .addResourceLocations(guildResourceLocation);

        // 이벤트 이미지 업로드 디렉토리 매핑
        String eventUploadPath = new File(eventImageProperties.getPath()).getAbsolutePath();
        String eventUrlPrefix = eventImageProperties.getUrlPrefix();
        String eventResourceLocation = "file:" + eventUploadPath + "/";
        log.info("이벤트 이미지 리소스 핸들러: {} -> {}", eventUrlPrefix + "/**", eventResourceLocation);
        registry.addResourceHandler(eventUrlPrefix + "/**")
                .addResourceLocations(eventResourceLocation);

        // 정적 리소스 핸들러는 마지막에 등록 (catch-all)
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(JWT_EXCLUDE_PATTERNS);

        registry.addInterceptor(multipartInterceptor)
                .addPathPatterns("/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
