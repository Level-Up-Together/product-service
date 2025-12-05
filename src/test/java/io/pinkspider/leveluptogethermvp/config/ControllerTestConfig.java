package io.pinkspider.leveluptogethermvp.config;

import static org.mockito.Mockito.mock;

import io.pinkspider.leveluptogethermvp.userservice.core.util.JwtUtil;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.MultiDeviceTokenService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
public class ControllerTestConfig {

    @Bean
    public JwtUtil jwtUtil() {
        return mock(JwtUtil.class);
    }


    @Bean
    public MultiDeviceTokenService multiDeviceTokenService() {
        return mock(MultiDeviceTokenService.class);
    }

    // Security 완전 비활성화
    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
