package io.pinkspider.leveluptogethermvp.userservice.moderation.config;

import io.pinkspider.leveluptogethermvp.userservice.moderation.application.AwsRekognitionModerationService;
import io.pinkspider.leveluptogethermvp.userservice.moderation.application.ImageModerationService;
import io.pinkspider.leveluptogethermvp.userservice.moderation.application.NoOpImageModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 이미지 검증 서비스 설정
 *
 * moderation.image.provider 값에 따라 적절한 구현체를 빈으로 등록합니다.
 * - none (기본값): NoOpImageModerationService
 * - aws-rekognition: AwsRekognitionModerationService
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ModerationConfig {

    private final ModerationProperties moderationProperties;

    @Bean
    public ImageModerationService imageModerationService() {
        String provider = moderationProperties.getProvider();

        if (provider == null || provider.equalsIgnoreCase("none")) {
            log.info("이미지 검증 서비스: NoOp (비활성화)");
            return new NoOpImageModerationService();
        }

        if (provider.equalsIgnoreCase("aws-rekognition")) {
            log.info("이미지 검증 서비스: AWS Rekognition (활성화)");
            return new AwsRekognitionModerationService(moderationProperties);
        }

        log.warn("알 수 없는 이미지 검증 제공자: {}. NoOp 서비스를 사용합니다.", provider);
        return new NoOpImageModerationService();
    }
}
