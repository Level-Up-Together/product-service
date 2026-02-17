package io.pinkspider.global.moderation.aspect;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.moderation.annotation.ModerateImage;
import io.pinkspider.global.moderation.application.ImageModerationService;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * @ModerateImage 어노테이션이 적용된 메서드에서 MultipartFile 파라미터를
 * 자동으로 탐색하여 이미지 모더레이션을 수행하는 AOP Aspect.
 *
 * 모더레이션이 비활성화(provider=none)된 환경에서는 검증을 건너뜁니다.
 * 부적절한 이미지가 감지되면 CustomException을 throw합니다.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageModerationAspect {

    private static final String MODERATION_ERROR_CODE = "000010";
    private static final String MODERATION_ERROR_MESSAGE = "부적절한 이미지가 감지되었습니다. 다른 이미지를 사용해주세요.";

    private final ImageModerationService imageModerationService;

    @Around("@annotation(moderateImage)")
    public Object moderateImage(ProceedingJoinPoint joinPoint, ModerateImage moderateImage) throws Throwable {
        if (!imageModerationService.isEnabled()) {
            return joinPoint.proceed();
        }

        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof MultipartFile imageFile) {
                if (imageFile.isEmpty()) {
                    continue;
                }
                ImageModerationResult result = imageModerationService.analyzeImage(imageFile);
                if (!result.isSafe()) {
                    log.warn("부적절한 이미지 업로드 차단: method={}, 사유={}",
                        joinPoint.getSignature().toShortString(),
                        result.getRejectionReason());
                    throw new CustomException(MODERATION_ERROR_CODE, MODERATION_ERROR_MESSAGE);
                }
            }
        }

        return joinPoint.proceed();
    }
}
