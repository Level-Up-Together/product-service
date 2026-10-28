package io.pinkspider.global.config;

import io.pinkspider.global.i18n.LocaleInterceptor;
import io.pinkspider.global.interceptor.JwtInterceptor;
import io.pinkspider.global.interceptor.MultipartInterceptor;
import io.pinkspider.global.resolver.CurrentUserArgumentResolver;
import io.pinkspider.leveluptogethermvp.statsservice.activity.api.UserActivityInterceptor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
        "/uploads/**", "/favicon.ico", "/error"
    };

    // LUT-598: 활동(DAU) 수집 — 인증된 사용자 요청만. 내부 API·테스트 로그인·스토어 웹훅은 사람의 활동이 아니다
    private static final String[] ACTIVITY_INCLUDE_PATTERNS = {
        "/api/**", "/jwt/reissue", "/graphql"
    };
    private static final String[] ACTIVITY_EXCLUDE_PATTERNS = {
        "/api/internal/**", "/api/test/**", "/api/v1/webhooks/**"
    };
    private final LocaleInterceptor localeInterceptor;
    private final JwtInterceptor jwtInterceptor;
    private final MultipartInterceptor multipartInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    // LUT-598: @WebMvcTest 슬라이스에는 이 빈이 없다 — 있을 때만 등록
    private final ObjectProvider<UserActivityInterceptor> userActivityInterceptorProvider;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 정적 리소스 핸들러 (catch-all)
        // 이미지 리소스 핸들러는 각 서비스별 WebMvcConfigurer에서 등록
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeInterceptor).addPathPatterns("/**");

        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(JWT_EXCLUDE_PATTERNS);

        registry.addInterceptor(multipartInterceptor).addPathPatterns("/**");

        UserActivityInterceptor activityInterceptor =
                userActivityInterceptorProvider.getIfAvailable();
        if (activityInterceptor != null) {
            registry.addInterceptor(activityInterceptor)
                    .addPathPatterns(ACTIVITY_INCLUDE_PATTERNS)
                    .excludePathPatterns(ACTIVITY_EXCLUDE_PATTERNS);
        }
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
