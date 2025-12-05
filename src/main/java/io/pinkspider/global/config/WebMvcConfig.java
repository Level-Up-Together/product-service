package io.pinkspider.global.config;

import io.pinkspider.global.interceptor.JwtInterceptor;
import io.pinkspider.global.interceptor.MultipartInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String[] JWT_EXCLUDE_PATTERNS = {
//        "/error",
//        "/healthCheck/_check",
//        "/favicon.ico",
//        Constant.API_EXTERNAL_PATH + Constant.API_V1_0_PATH + "/user/login",
//        Constant.API_EXTERNAL_PATH + Constant.API_V1_0_PATH + "/user/signup",
//        Constant.API_EXTERNAL_PATH + Constant.API_V1_0_PATH + "/user/signup/complete"
    };
    private final JwtInterceptor jwtInterceptor;
    private final MultipartInterceptor multipartInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
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
}
