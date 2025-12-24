package io.pinkspider.global.validation;

import io.pinkspider.global.annotation.NoProfanity;
import io.pinkspider.leveluptogethermvp.profanity.application.ProfanityDetectionEngine;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityDetectionResult;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @NoProfanity 어노테이션 검증기
 * <p>
 * Spring Bean으로 등록되어 ProfanityDetectionEngine을 주입받습니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoProfanityValidator implements ConstraintValidator<NoProfanity, String> {

    private final ProfanityDetectionEngine detectionEngine;

    private ProfanityDetectionMode mode;
    private String fieldName;
    private boolean checkKoreanJamo;
    private int levenshteinThreshold;

    @Override
    public void initialize(NoProfanity annotation) {
        this.mode = annotation.mode();
        this.fieldName = annotation.fieldName();
        this.checkKoreanJamo = annotation.checkKoreanJamo();
        this.levenshteinThreshold = annotation.levenshteinThreshold();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null 또는 빈 값은 통과 (@NotBlank로 별도 검증)
        if (value == null || value.trim().isEmpty()) {
            return true;
        }

        ProfanityDetectionResult result = detectionEngine.detect(
            value,
            mode,
            checkKoreanJamo,
            levenshteinThreshold
        );

        if (result.isDetected()) {
            log.warn("비속어 탐지 - 필드: {}, 탐지어: {}, 매칭유형: {}, 모드: {}",
                fieldName.isEmpty() ? "unknown" : fieldName,
                result.getDetectedWord(),
                result.getMatchType(),
                mode);

            // 커스텀 에러 메시지 설정
            context.disableDefaultConstraintViolation();

            String message;
            if (fieldName.isEmpty()) {
                message = "부적절한 표현이 포함되어 있습니다.";
            } else {
                message = fieldName + "에 부적절한 표현이 포함되어 있습니다.";
            }

            context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();

            return false;
        }

        return true;
    }
}
