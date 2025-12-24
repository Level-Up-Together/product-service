package io.pinkspider.global.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.annotation.NoProfanity;
import io.pinkspider.leveluptogethermvp.profanity.application.ProfanityDetectionEngine;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityDetectionResult;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoProfanityValidatorTest {

    @Mock
    private ProfanityDetectionEngine detectionEngine;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    private NoProfanityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NoProfanityValidator(detectionEngine);
    }

    private NoProfanity createMockAnnotation(
        String message,
        ProfanityDetectionMode mode,
        String fieldName,
        boolean checkKoreanJamo,
        int levenshteinThreshold
    ) {
        NoProfanity annotation = mock(NoProfanity.class);
        when(annotation.message()).thenReturn(message);
        when(annotation.mode()).thenReturn(mode);
        when(annotation.fieldName()).thenReturn(fieldName);
        when(annotation.checkKoreanJamo()).thenReturn(checkKoreanJamo);
        when(annotation.levenshteinThreshold()).thenReturn(levenshteinThreshold);
        return annotation;
    }

    @Nested
    @DisplayName("유효성 검사")
    class ValidationTest {

        @Test
        @DisplayName("null 값은 유효하다")
        void nullIsValid() {
            NoProfanity annotation = createMockAnnotation(
                "부적절한 표현이 포함되어 있습니다.",
                ProfanityDetectionMode.NORMAL,
                "",
                true,
                0
            );
            validator.initialize(annotation);

            boolean result = validator.isValid(null, context);

            assertThat(result).isTrue();
            verify(detectionEngine, never()).detect(anyString(), any(), anyBoolean(), anyInt());
        }

        @Test
        @DisplayName("빈 문자열은 유효하다")
        void emptyStringIsValid() {
            NoProfanity annotation = createMockAnnotation(
                "부적절한 표현이 포함되어 있습니다.",
                ProfanityDetectionMode.NORMAL,
                "",
                true,
                0
            );
            validator.initialize(annotation);

            boolean result = validator.isValid("", context);

            assertThat(result).isTrue();
            verify(detectionEngine, never()).detect(anyString(), any(), anyBoolean(), anyInt());
        }

        @Test
        @DisplayName("공백만 있는 문자열은 유효하다")
        void whitespaceOnlyIsValid() {
            NoProfanity annotation = createMockAnnotation(
                "부적절한 표현이 포함되어 있습니다.",
                ProfanityDetectionMode.NORMAL,
                "",
                true,
                0
            );
            validator.initialize(annotation);

            boolean result = validator.isValid("   ", context);

            assertThat(result).isTrue();
            verify(detectionEngine, never()).detect(anyString(), any(), anyBoolean(), anyInt());
        }

        @Test
        @DisplayName("비속어가 없는 텍스트는 유효하다")
        void textWithoutProfanityIsValid() {
            NoProfanity annotation = createMockAnnotation(
                "부적절한 표현이 포함되어 있습니다.",
                ProfanityDetectionMode.NORMAL,
                "",
                true,
                0
            );
            validator.initialize(annotation);

            when(detectionEngine.detect(
                anyString(),
                any(ProfanityDetectionMode.class),
                anyBoolean(),
                anyInt()
            )).thenReturn(ProfanityDetectionResult.notDetected());

            boolean result = validator.isValid("좋은 하루 되세요", context);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("비속어가 있는 텍스트는 유효하지 않다")
        void textWithProfanityIsInvalid() {
            NoProfanity annotation = createMockAnnotation(
                "부적절한 표현이 포함되어 있습니다.",
                ProfanityDetectionMode.NORMAL,
                "제목",
                true,
                0
            );
            validator.initialize(annotation);

            when(detectionEngine.detect(
                anyString(),
                any(ProfanityDetectionMode.class),
                anyBoolean(),
                anyInt()
            )).thenReturn(ProfanityDetectionResult.detected("시발", "NORMALIZED_MATCH"));

            when(context.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(violationBuilder);

            boolean result = validator.isValid("시발 뭐야", context);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("필드명이 있으면 에러 메시지에 포함된다")
        void fieldNameIncludedInErrorMessage() {
            NoProfanity annotation = createMockAnnotation(
                "부적절한 표현이 포함되어 있습니다.",
                ProfanityDetectionMode.NORMAL,
                "미션 제목",
                true,
                0
            );
            validator.initialize(annotation);

            when(detectionEngine.detect(
                anyString(),
                any(ProfanityDetectionMode.class),
                anyBoolean(),
                anyInt()
            )).thenReturn(ProfanityDetectionResult.detected("시발", "NORMALIZED_MATCH"));

            when(context.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(violationBuilder);

            validator.isValid("시발 뭐야", context);

            verify(context).buildConstraintViolationWithTemplate(
                org.mockito.ArgumentMatchers.contains("미션 제목")
            );
        }

        @Test
        @DisplayName("STRICT 모드로 검증할 수 있다")
        void canValidateInStrictMode() {
            NoProfanity annotation = createMockAnnotation(
                "부적절한 표현이 포함되어 있습니다.",
                ProfanityDetectionMode.STRICT,
                "",
                true,
                1
            );
            validator.initialize(annotation);

            when(detectionEngine.detect(
                anyString(),
                any(ProfanityDetectionMode.class),
                anyBoolean(),
                anyInt()
            )).thenReturn(ProfanityDetectionResult.notDetected());

            boolean result = validator.isValid("좋은 텍스트", context);

            assertThat(result).isTrue();
            verify(detectionEngine).detect(
                "좋은 텍스트",
                ProfanityDetectionMode.STRICT,
                true,
                1
            );
        }

        @Test
        @DisplayName("LENIENT 모드로 검증할 수 있다")
        void canValidateInLenientMode() {
            NoProfanity annotation = createMockAnnotation(
                "부적절한 표현이 포함되어 있습니다.",
                ProfanityDetectionMode.LENIENT,
                "",
                false,
                0
            );
            validator.initialize(annotation);

            when(detectionEngine.detect(
                anyString(),
                any(ProfanityDetectionMode.class),
                anyBoolean(),
                anyInt()
            )).thenReturn(ProfanityDetectionResult.notDetected());

            boolean result = validator.isValid("좋은 텍스트", context);

            assertThat(result).isTrue();
            verify(detectionEngine).detect(
                "좋은 텍스트",
                ProfanityDetectionMode.LENIENT,
                false,
                0
            );
        }
    }
}
