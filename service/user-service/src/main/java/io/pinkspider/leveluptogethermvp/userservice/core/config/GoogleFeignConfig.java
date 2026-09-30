package io.pinkspider.leveluptogethermvp.userservice.core.config;

import feign.Client;
import feign.codec.Encoder;
import feign.form.FormEncoder;
import feign.form.spring.SpringFormEncoder;
import feign.httpclient.ApacheHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.context.annotation.Bean;

// @Configuration
public class GoogleFeignConfig {

    // 🔥 Apache HttpClient를 Feign의 기본 HTTP 클라이언트로 설정
    @Bean
    public Client feignClient() {
        return new ApacheHttpClient(HttpClients.createDefault());
    }

    // 🔥 Form URL Encoding을 위한 Encoder 설정
    @Bean
    public Encoder feignFormEncoder() {
        return new FormEncoder(new SpringFormEncoder());
    }
}
