package io.pinkspider.global.moderation.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 이미지 검증 관련 설정 프로퍼티
 *
 * 설정 예시:
 * moderation:
 *   image:
 *     provider: none  # none, onnx-nsfw, aws-rekognition
 *     min-confidence: 80.0
 *     blocked-categories:
 *       - Explicit Nudity
 *       - Violence
 *       - Visually Disturbing
 *     onnx:
 *       model-path: classpath:models/nsfw.onnx
 *       nsfw-threshold: 0.8
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "moderation.image")
public class ModerationProperties {

    /**
     * 검증 제공자 (none, onnx-nsfw, aws-rekognition)
     * - none: 검증 비활성화 (기본값)
     * - onnx-nsfw: ONNX NSFW 모델 사용 (비용 절감)
     * - aws-rekognition: AWS Rekognition 사용
     */
    private String provider = "none";

    /**
     * 최소 신뢰도 임계값 (0.0 ~ 100.0)
     * 이 값 이상의 신뢰도로 감지된 레이블만 차단 대상으로 처리
     */
    private double minConfidence = 80.0;

    /**
     * 차단할 카테고리 목록
     * AWS Rekognition 기준 카테고리명 사용
     */
    private List<String> blockedCategories = List.of(
        "Explicit Nudity",
        "Violence",
        "Visually Disturbing",
        "Hate Symbols"
    );

    /**
     * 검증 기능 활성화 여부
     */
    public boolean isEnabled() {
        return provider != null && !provider.equalsIgnoreCase("none");
    }

    /**
     * AWS 관련 설정 (aws-rekognition 사용 시)
     */
    private AwsConfig aws = new AwsConfig();

    /**
     * ONNX 관련 설정 (onnx-nsfw 사용 시)
     */
    private OnnxConfig onnx = new OnnxConfig();

    @Getter
    @Setter
    public static class AwsConfig {
        /**
         * AWS 리전 (예: ap-northeast-2)
         */
        private String region = "ap-northeast-2";

        /**
         * Access Key ID (환경변수 또는 IAM Role 사용 권장)
         */
        private String accessKeyId;

        /**
         * Secret Access Key (환경변수 또는 IAM Role 사용 권장)
         */
        private String secretAccessKey;
    }

    @Getter
    @Setter
    public static class OnnxConfig {
        /**
         * ONNX 모델 파일 경로 (classpath: 또는 절대 경로)
         */
        private String modelPath = "classpath:models/nsfw.onnx";

        /**
         * NSFW 판정 임계값 (0.0 ~ 1.0)
         * 이 값 이상이면 부적절한 이미지로 판정
         */
        private float nsfwThreshold = 0.8f;
    }
}
