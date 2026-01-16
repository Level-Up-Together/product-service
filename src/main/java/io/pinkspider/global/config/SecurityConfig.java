package io.pinkspider.global.config;


import io.pinkspider.leveluptogethermvp.userservice.core.component.AuthEntryPointJwt;
import io.pinkspider.leveluptogethermvp.userservice.core.filter.JwtAuthenticationFilter;
import io.pinkspider.leveluptogethermvp.userservice.core.properties.OAuth2Properties;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@Profile("!test")
public class SecurityConfig {

    @Value("${management.endpoints.web.base-path}")
    private String ACTUATOR_PATH;

    private final AuthEntryPointJwt unauthorizedHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2Properties oAuth2Properties;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // OAuth2Properties의 allowedOrigins 사용 (credentials: include 지원)
        List<String> allowedOrigins = oAuth2Properties.getAllowedOrigins();
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            configuration.setAllowedOrigins(allowedOrigins);
            configuration.setAllowCredentials(true);
        } else {
            configuration.setAllowedOrigins(List.of("*"));
            configuration.setAllowCredentials(false);
        }
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("X-Requested-With", "Content-Type", "Authorization", "X-XSRF-token", "Origin", "Accept"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthEntryPointJwt entryPoint) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
            .authorizeHttpRequests(authorizeHttpRequests ->
                authorizeHttpRequests
                    // 공개 엔드포인트
                    .requestMatchers(new AntPathRequestMatcher(ACTUATOR_PATH + "/**")).permitAll()
                    .requestMatchers("/favicon.ico", "/error").permitAll()
                    // 정적 리소스 (이미지 업로드)
                    .requestMatchers("/uploads/**").permitAll()
                    // WebSocket 엔드포인트
                    .requestMatchers("/ws/**").permitAll()

                    // 인증 관련 API (로그인, 회원가입, OAuth)
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/api/v1/oauth/**").permitAll()
                    .requestMatchers("/oauth/**").permitAll()  // OAuth 컨트롤러 (/oauth/uri/*, /oauth/callback/*)
                    .requestMatchers("/oauth2/**").permitAll()
                    .requestMatchers("/login/oauth2/**").permitAll()
                    // JWT 토큰 재발급 - refresh_token 검증으로 보안 확보
                    .requestMatchers("/jwt/reissue").permitAll()

                    // 내부 캐시 관리 API (운영용)
                    .requestMatchers("/api/v1/bff/season/cache").permitAll()

                    // 관리자 전용 API
                    .requestMatchers("/api/v1/users/experience/levels").hasRole("ADMIN")
                    .requestMatchers("/api/v1/attendance/init").hasRole("ADMIN")

                    // 나머지는 인증 필요
                    .anyRequest().authenticated())
            .logout(logout -> logout.logoutSuccessUrl("/"))
            // JWT 필터 추가
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        http.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));

        return http.build();
    }
}
