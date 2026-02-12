package io.pinkspider.leveluptogethermvp.userservice.moderation.application;

import io.pinkspider.leveluptogethermvp.userservice.moderation.domain.dto.ImageModerationResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 콘텐츠 검증 서비스 인터페이스
 *
 * 구현체:
 * - NoOpImageModerationService: 검증 비활성화 (개발/테스트용)
 * - AwsRekognitionModerationService: AWS Rekognition 기반 검증 (운영용)
 */
public interface ImageModerationService {

    /**
     * 업로드된 이미지 파일을 분석하여 부적절한 콘텐츠 여부 검사
     *
     * @param imageFile 분석할 이미지 파일
     * @return 검증 결과
     */
    ImageModerationResult analyzeImage(MultipartFile imageFile);

    /**
     * URL로 지정된 이미지를 분석하여 부적절한 콘텐츠 여부 검사
     *
     * @param imageUrl 분석할 이미지 URL
     * @return 검증 결과
     */
    ImageModerationResult analyzeImageUrl(String imageUrl);

    /**
     * 이미지 검증 기능 활성화 여부
     *
     * @return true: 활성화됨, false: 비활성화됨
     */
    boolean isEnabled();

    /**
     * 사용 중인 검증 제공자 이름
     *
     * @return 제공자 이름 (none, aws-rekognition 등)
     */
    String getProviderName();
}
