package io.pinkspider.leveluptogethermvp.userservice.moderation.application;

import io.pinkspider.leveluptogethermvp.userservice.moderation.config.ModerationProperties;
import io.pinkspider.leveluptogethermvp.userservice.moderation.domain.dto.ImageModerationResult;
import io.pinkspider.leveluptogethermvp.userservice.moderation.domain.dto.ModerationLabel;
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
    // 운영 시 활성화: private final RekognitionClient rekognitionClient;

    public AwsRekognitionModerationService(ModerationProperties properties) {
        this.properties = properties;
        log.info("AwsRekognitionModerationService 초기화 - 리전: {}, 최소신뢰도: {}",
            properties.getAws().getRegion(),
            properties.getMinConfidence());

        // TODO: 운영 시 AWS SDK 클라이언트 초기화
        // this.rekognitionClient = RekognitionClient.builder()
        //     .region(Region.of(properties.getAws().getRegion()))
        //     .credentialsProvider(...)
        //     .build();
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
            // 파일 읽기 실패 시 안전한 것으로 처리 (운영 정책에 따라 변경 가능)
            return ImageModerationResult.safe();
        }
    }

    @Override
    public ImageModerationResult analyzeImageUrl(String imageUrl) {
        log.info("AWS Rekognition URL 이미지 분석 시작: URL={}", imageUrl);

        // TODO: 운영 시 S3 URL 또는 외부 URL 분석 구현
        // DetectModerationLabelsRequest request = DetectModerationLabelsRequest.builder()
        //     .image(Image.builder().s3Object(...).build())
        //     .minConfidence((float) properties.getMinConfidence())
        //     .build();

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

    /**
     * 이미지 바이트 배열 분석
     */
    private ImageModerationResult analyzeImageBytes(byte[] imageBytes) {
        // TODO: 운영 시 AWS SDK 호출로 대체
        // DetectModerationLabelsRequest request = DetectModerationLabelsRequest.builder()
        //     .image(Image.builder().bytes(SdkBytes.fromByteArray(imageBytes)).build())
        //     .minConfidence((float) properties.getMinConfidence())
        //     .build();
        //
        // DetectModerationLabelsResponse response = rekognitionClient.detectModerationLabels(request);
        // List<software.amazon.awssdk.services.rekognition.model.ModerationLabel> labels = response.moderationLabels();

        // 스켈레톤: 실제 AWS 호출 없이 안전한 결과 반환
        log.debug("AWS Rekognition 스켈레톤 모드: 실제 API 호출 없이 안전한 것으로 처리");
        return ImageModerationResult.safe();
    }

    /**
     * AWS Rekognition 응답을 내부 DTO로 변환
     * (운영 시 사용)
     */
    @SuppressWarnings("unused")
    private ImageModerationResult processLabels(List<Object> awsLabels) {
        List<ModerationLabel> detectedLabels = new ArrayList<>();
        Map<String, Double> categoryScores = new HashMap<>();
        List<String> blockedCategories = properties.getBlockedCategories();

        boolean hasBlockedContent = false;
        String rejectionReason = null;

        // TODO: 운영 시 실제 변환 로직 구현
        // for (software.amazon.awssdk.services.rekognition.model.ModerationLabel label : awsLabels) {
        //     ModerationLabel dto = ModerationLabel.builder()
        //         .category(label.parentName() != null ? label.parentName() : label.name())
        //         .name(label.name())
        //         .confidence(label.confidence())
        //         .parentName(label.parentName())
        //         .build();
        //     detectedLabels.add(dto);
        //
        //     String category = dto.getCategory();
        //     categoryScores.merge(category, dto.getConfidence(), Math::max);
        //
        //     if (blockedCategories.contains(category) && dto.getConfidence() >= properties.getMinConfidence()) {
        //         hasBlockedContent = true;
        //         if (rejectionReason == null) {
        //             rejectionReason = "부적절한 콘텐츠가 감지되었습니다: " + category;
        //         }
        //     }
        // }

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
