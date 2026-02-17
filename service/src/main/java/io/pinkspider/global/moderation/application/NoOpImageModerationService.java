package io.pinkspider.global.moderation.application;

import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 검증을 수행하지 않는 NoOp 구현체
 *
 * 개발 및 테스트 환경에서 사용됩니다.
 * 모든 이미지를 안전한 것으로 처리합니다.
 */
@Slf4j
public class NoOpImageModerationService implements ImageModerationService {

    public NoOpImageModerationService() {
        log.info("NoOpImageModerationService 초기화 - 이미지 검증이 비활성화되었습니다.");
    }

    @Override
    public ImageModerationResult analyzeImage(MultipartFile imageFile) {
        log.debug("NoOp 이미지 검증: 파일={}, 크기={} bytes - 검증 생략",
            imageFile.getOriginalFilename(),
            imageFile.getSize());
        return ImageModerationResult.safe();
    }

    @Override
    public ImageModerationResult analyzeImageUrl(String imageUrl) {
        log.debug("NoOp 이미지 검증: URL={} - 검증 생략", imageUrl);
        return ImageModerationResult.safe();
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String getProviderName() {
        return "none";
    }
}
