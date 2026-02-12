package io.pinkspider.global.config;

import io.pinkspider.global.interceptor.JwtInterceptor;
import io.pinkspider.global.interceptor.MultipartInterceptor;
import io.pinkspider.global.resolver.CurrentUserArgumentResolver;
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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 정적 리소스 핸들러 (catch-all)
        // 이미지 리소스 핸들러는 각 서비스별 WebMvcConfigurer에서 등록
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
