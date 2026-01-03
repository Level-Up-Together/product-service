package io.pinkspider.leveluptogethermvp.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.validation.KoreanTextNormalizer;
import io.pinkspider.global.validation.NoProfanityValidator;
import io.pinkspider.global.validation.ProfanityDetectionMode;
import io.pinkspider.leveluptogethermvp.profanity.application.ProfanityDetectionEngine;
import io.pinkspider.leveluptogethermvp.profanity.application.ProfanityValidationService;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityDetectionResult;
import io.pinkspider.leveluptogethermvp.userservice.core.util.JwtUtil;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildImageProperties;
import io.pinkspider.leveluptogethermvp.missionservice.application.MissionImageProperties;
import io.pinkspider.leveluptogethermvp.userservice.mypage.application.ProfileImageProperties;
import io.pinkspider.leveluptogethermvp.userservice.oauth.application.MultiDeviceTokenService;
import java.util.List;
import java.util.Set;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@TestConfiguration
public class ControllerTestConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new PageableHandlerMethodArgumentResolver());
    }

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

    // 비속어 검증 관련 Mock 빈들
    @Bean
    public KoreanTextNormalizer koreanTextNormalizer() {
        return new KoreanTextNormalizer();
    }

    @Bean
    public ProfanityValidationService profanityValidationService() {
        ProfanityValidationService mock = mock(ProfanityValidationService.class);
        when(mock.getActiveProfanityWords()).thenReturn(Set.of());
        return mock;
    }

    @Bean
    @Primary
    public ProfanityDetectionEngine profanityDetectionEngine(
            ProfanityValidationService profanityValidationService,
            KoreanTextNormalizer koreanTextNormalizer) {
        ProfanityDetectionEngine mock = mock(ProfanityDetectionEngine.class);
        when(mock.detect(anyString(), any(ProfanityDetectionMode.class), anyBoolean(), anyInt()))
            .thenReturn(ProfanityDetectionResult.notDetected());
        return mock;
    }

    @Bean
    @Primary
    public NoProfanityValidator noProfanityValidator(ProfanityDetectionEngine profanityDetectionEngine) {
        return new NoProfanityValidator(profanityDetectionEngine);
    }

    // 프로필 이미지 업로드 설정 Mock
    @Bean
    public ProfileImageProperties profileImageProperties() {
        ProfileImageProperties properties = new ProfileImageProperties();
        properties.setPath("/tmp/test-uploads/profile");
        properties.setMaxSize(5242880);
        properties.setAllowedExtensions("jpg,jpeg,png,gif,webp");
        properties.setUrlPrefix("/uploads/profile");
        return properties;
    }

    // 미션 이미지 업로드 설정 Mock
    @Bean
    public MissionImageProperties missionImageProperties() {
        MissionImageProperties properties = new MissionImageProperties();
        properties.setPath("/tmp/test-uploads/missions");
        properties.setMaxSize(5242880);
        properties.setAllowedExtensions("jpg,jpeg,png,gif,webp");
        properties.setUrlPrefix("/uploads/missions");
        return properties;
    }

    // 길드 이미지 업로드 설정 Mock
    @Bean
    public GuildImageProperties guildImageProperties() {
        GuildImageProperties properties = new GuildImageProperties();
        properties.setPath("/tmp/test-uploads/guild");
        properties.setMaxSize(10485760);
        properties.setAllowedExtensions("jpg,jpeg,png,gif,webp");
        properties.setUrlPrefix("/uploads/guild");
        return properties;
    }
}
