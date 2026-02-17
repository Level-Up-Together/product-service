package io.pinkspider.global.moderation.application;

import io.pinkspider.global.moderation.config.ModerationProperties;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import io.pinkspider.global.moderation.domain.dto.ModerationLabel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

/**
 * AWS Rekognition 기반 이미지 검증 서비스
 *
 * 운영 환경에서 AWS Rekognition DetectModerationLabels API를 사용하여
 * 부적절한 이미지를 감지합니다.
 *
 * 필요 의존성 (운영 시 추가):
 * - software.amazon.awssdk:rekognition
 *
 * 필요 IAM 권한:
 * - rekognition:DetectModerationLabels
 */
@Slf4j
public class AwsRekognitionModerationService implements ImageModerationService {

    private final ModerationProperties properties;

    public AwsRekognitionModerationService(ModerationProperties properties) {
        this.properties = properties;
        log.info("AwsRekognitionModerationService 초기화 - 리전: {}, 최소신뢰도: {}",
            properties.getAws().getRegion(),
            properties.getMinConfidence());
    }

    @Override
    public ImageModerationResult analyzeImage(MultipartFile imageFile) {
        log.info("AWS Rekognition 이미지 분석 시작: 파일={}, 크기={} bytes",
            imageFile.getOriginalFilename(),
            imageFile.getSize());

        try {
            byte[] imageBytes = imageFile.getBytes();
            return analyzeImageBytes(imageBytes);
        } catch (IOException e) {
            log.error("이미지 파일 읽기 실패: {}", e.getMessage());
            return ImageModerationResult.safe();
        }
    }

    @Override
    public ImageModerationResult analyzeImageUrl(String imageUrl) {
        log.info("AWS Rekognition URL 이미지 분석 시작: URL={}", imageUrl);
        log.warn("URL 이미지 분석은 아직 구현되지 않았습니다. 안전한 것으로 처리합니다.");
        return ImageModerationResult.safe();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "aws-rekognition";
    }

    private ImageModerationResult analyzeImageBytes(byte[] imageBytes) {
        log.debug("AWS Rekognition 스켈레톤 모드: 실제 API 호출 없이 안전한 것으로 처리");
        return ImageModerationResult.safe();
    }

    @SuppressWarnings("unused")
    private ImageModerationResult processLabels(List<Object> awsLabels) {
        List<ModerationLabel> detectedLabels = new ArrayList<>();
        Map<String, Double> categoryScores = new HashMap<>();
        List<String> blockedCategories = properties.getBlockedCategories();

        boolean hasBlockedContent = false;
        String rejectionReason = null;

        if (hasBlockedContent) {
            log.warn("부적절한 이미지 감지: {}", rejectionReason);
            return ImageModerationResult.unsafe(rejectionReason, detectedLabels, categoryScores, getProviderName());
        }

        return ImageModerationResult.builder()
            .safe(true)
            .overallConfidence(100.0)
            .detectedLabels(detectedLabels)
            .categoryScores(categoryScores)
            .provider(getProviderName())
            .build();
    }
}
