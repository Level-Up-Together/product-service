package io.pinkspider.global.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

@RequiredArgsConstructor
public class FeignRequestInterceptor implements RequestInterceptor {

    @Value("${spring.application.name}")
    private String serviceName;

    @Override
    public void apply(RequestTemplate requestTemplate) {
        requestTemplate.header("Source-Uri", serviceName);
    }
}
