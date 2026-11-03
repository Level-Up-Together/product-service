package io.pinkspider.global.moderation.aspect;

import io.pinkspider.global.moderation.annotation.ModerateImage;
import io.pinkspider.global.moderation.application.ImageModerationMetrics;
import io.pinkspider.global.moderation.application.ImageModerationService;
import io.pinkspider.global.moderation.config.ModerationProperties;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import io.pinkspider.global.moderation.exception.ImageModerationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code @ModerateImage} 가 붙은 메서드의 이미지 인자를 찾아 검수하는 AOP Aspect.
 *
 * <p>검수 대상 인자는 {@link MultipartFile} 하나뿐 아니라 {@code List<MultipartFile>}·{@code MultipartFile[]} 도
 * 순회한다 (LUT-650 — 예전엔 단일 인자만 봐서 미션 인증 사진({@code List<MultipartFile>})이 검수 없이 통과했다). 빈 파일은 건너뛴다.
 *
 * <p>검수 자체가 실패(파일 읽기 실패·미지원 포맷·추론 예외·제공자 런타임 예외)하면 {@code moderation.image.fail-open}(기본 true)에 따라
 * 통과시키거나(현행 유지 — error 로그 + {@code lut_image_moderation_fail_open_total}) 차단한다({@code 000011},
 * 503). 어느 쪽이든 조용히 무력화되지는 않는다. 모더레이션이 비활성화(provider=none)된 환경에서는 검증을 건너뛴다.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageModerationAspect {

    static final String MODERATION_ERROR_CODE = ImageModerationException.BLOCKED_CODE;
    static final String MODERATION_ERROR_MESSAGE = ImageModerationException.BLOCKED_MESSAGE_KEY;
    static final String MODERATION_UNAVAILABLE_CODE = ImageModerationException.UNAVAILABLE_CODE;
    static final String MODERATION_UNAVAILABLE_MESSAGE =
            ImageModerationException.UNAVAILABLE_MESSAGE_KEY;

    private final ImageModerationService imageModerationService;
    private final ModerationProperties moderationProperties;
    private final ImageModerationMetrics metrics;

    @Around("@annotation(moderateImage)")
    public Object moderateImage(ProceedingJoinPoint joinPoint, ModerateImage moderateImage)
            throws Throwable {
        if (!imageModerationService.isEnabled()) {
            return joinPoint.proceed();
        }

        String provider = imageModerationService.getProviderName();
        for (MultipartFile imageFile : collectImageFiles(joinPoint.getArgs())) {
            ImageModerationResult result = analyze(imageFile, provider);
            if (result.isFailOpen()) {
                handleFailure(joinPoint, imageFile, provider, result.getFailureReason());
                continue;
            }
            if (!result.isSafe()) {
                metrics.checked(provider, ImageModerationMetrics.RESULT_BLOCKED);
                log.warn(
                        "부적절한 이미지 업로드 차단: method={}, file={}, 사유={}",
                        joinPoint.getSignature().toShortString(),
                        imageFile.getOriginalFilename(),
                        result.getRejectionReason());
                throw ImageModerationException
                        .blocked(); // LUT-714: 400 (ImageModerationExceptionHandler)
            }
            metrics.checked(provider, ImageModerationMetrics.RESULT_SAFE);
        }

        return joinPoint.proceed();
    }

    /** 제공자가 런타임 예외(또는 null)를 돌려줘도 fail-open 결과로 바꿔, 통과/차단 정책을 한곳에서 적용한다. */
    private ImageModerationResult analyze(MultipartFile imageFile, String provider) {
        try {
            ImageModerationResult result = imageModerationService.analyzeImage(imageFile);
            if (result == null) {
                return ImageModerationResult.failOpen(
                        ImageModerationResult.REASON_EXCEPTION, provider);
            }
            return result;
        } catch (RuntimeException e) {
            log.error(
                    "이미지 검수 중 예외: provider={}, file={}",
                    provider,
                    imageFile.getOriginalFilename(),
                    e);
            return ImageModerationResult.failOpen(ImageModerationResult.REASON_EXCEPTION, provider);
        }
    }

    private void handleFailure(
            ProceedingJoinPoint joinPoint,
            MultipartFile imageFile,
            String provider,
            String reason) {
        metrics.failOpen(provider, reason);
        String method = joinPoint.getSignature().toShortString();
        if (moderationProperties.isFailOpen()) {
            log.error(
                    "이미지 검수 실패 → 통과(fail-open): method={}, file={}, provider={}, reason={}",
                    method,
                    imageFile.getOriginalFilename(),
                    provider,
                    reason);
            return;
        }
        log.error(
                "이미지 검수 실패 → 차단(fail-closed): method={}, file={}, provider={}, reason={}",
                method,
                imageFile.getOriginalFilename(),
                provider,
                reason);
        throw ImageModerationException.unavailable(); // LUT-714: 503
    }

    /**
     * 인자에서 검수할 파일을 모은다: MultipartFile · MultipartFile[] · Collection 안의 MultipartFile. 빈 파일·null
     * 제외, 순서 유지.
     */
    static List<MultipartFile> collectImageFiles(Object[] args) {
        List<MultipartFile> files = new ArrayList<>();
        if (args == null) {
            return files;
        }
        for (Object arg : args) {
            if (arg instanceof MultipartFile file) {
                addIfPresent(files, file);
            } else if (arg instanceof MultipartFile[] array) {
                for (MultipartFile file : array) {
                    addIfPresent(files, file);
                }
            } else if (arg instanceof Collection<?> collection) {
                for (Object element : collection) {
                    if (element instanceof MultipartFile file) {
                        addIfPresent(files, file);
                    }
                }
            }
        }
        return files;
    }

    private static void addIfPresent(List<MultipartFile> files, MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            files.add(file);
        }
    }
}
