package io.pinkspider.global.moderation.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.moderation.annotation.ModerateImage;
import io.pinkspider.global.moderation.application.ImageModerationService;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import io.pinkspider.global.moderation.domain.dto.ModerationLabel;
import java.util.List;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageModerationAspect 테스트")
class ImageModerationAspectTest {

    @Mock
    private ImageModerationService imageModerationService;

    @InjectMocks
    private ImageModerationAspect aspect;

    @Nested
    @DisplayName("모더레이션 비활성화 시")
    class WhenDisabled {

        @Test
        @DisplayName("모더레이션이 비활성화되면 검증 없이 원본 메서드를 실행한다")
        void shouldProceedWithoutModeration() throws Throwable {
            // given
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            ModerateImage annotation = mock(ModerateImage.class);
            when(imageModerationService.isEnabled()).thenReturn(false);
            when(joinPoint.proceed()).thenReturn("result");

            // when
            Object result = aspect.moderateImage(joinPoint, annotation);

            // then
            assertThat(result).isEqualTo("result");
            verify(imageModerationService, never()).analyzeImage(any());
        }
    }

    @Nested
    @DisplayName("모더레이션 활성화 시")
    class WhenEnabled {

        @Test
        @DisplayName("안전한 이미지면 원본 메서드를 실행한다")
        void shouldProceedWhenImageIsSafe() throws Throwable {
            // given
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            ModerateImage annotation = mock(ModerateImage.class);
            MultipartFile imageFile = mock(MultipartFile.class);

            when(imageModerationService.isEnabled()).thenReturn(true);
            when(imageFile.isEmpty()).thenReturn(false);
            when(imageModerationService.analyzeImage(imageFile)).thenReturn(ImageModerationResult.safe());
            when(joinPoint.getArgs()).thenReturn(new Object[]{"userId", imageFile});
            when(joinPoint.proceed()).thenReturn("result");

            // when
            Object result = aspect.moderateImage(joinPoint, annotation);

            // then
            assertThat(result).isEqualTo("result");
            verify(imageModerationService).analyzeImage(imageFile);
        }

        @Test
        @DisplayName("부적절한 이미지면 CustomException을 throw한다")
        void shouldThrowExceptionWhenImageIsUnsafe() throws Throwable {
            // given
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            ModerateImage annotation = mock(ModerateImage.class);
            MultipartFile imageFile = mock(MultipartFile.class);
            Signature signature = mock(Signature.class);

            ImageModerationResult unsafeResult = ImageModerationResult.unsafe(
                "NSFW 콘텐츠 감지",
                List.of(ModerationLabel.builder().category("NSFW").name("NSFW Content").confidence(95.0).build()),
                Map.of("NSFW", 95.0),
                "onnx-nsfw"
            );

            when(imageModerationService.isEnabled()).thenReturn(true);
            when(imageFile.isEmpty()).thenReturn(false);
            when(imageModerationService.analyzeImage(imageFile)).thenReturn(unsafeResult);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"userId", imageFile});
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.toShortString()).thenReturn("TestService.upload(..)");

            // when & then
            assertThatThrownBy(() -> aspect.moderateImage(joinPoint, annotation))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("부적절한 이미지가 감지되었습니다");

            verify(joinPoint, never()).proceed();
        }

        @Test
        @DisplayName("빈 이미지 파일은 검증을 건너뛴다")
        void shouldSkipEmptyFile() throws Throwable {
            // given
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            ModerateImage annotation = mock(ModerateImage.class);
            MultipartFile emptyFile = mock(MultipartFile.class);

            when(imageModerationService.isEnabled()).thenReturn(true);
            when(emptyFile.isEmpty()).thenReturn(true);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"userId", emptyFile});
            when(joinPoint.proceed()).thenReturn("result");

            // when
            Object result = aspect.moderateImage(joinPoint, annotation);

            // then
            assertThat(result).isEqualTo("result");
            verify(imageModerationService, never()).analyzeImage(any());
        }

        @Test
        @DisplayName("MultipartFile 파라미터가 없으면 검증 없이 실행한다")
        void shouldProceedWhenNoMultipartFile() throws Throwable {
            // given
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            ModerateImage annotation = mock(ModerateImage.class);

            when(imageModerationService.isEnabled()).thenReturn(true);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"userId", 123L});
            when(joinPoint.proceed()).thenReturn("result");

            // when
            Object result = aspect.moderateImage(joinPoint, annotation);

            // then
            assertThat(result).isEqualTo("result");
            verify(imageModerationService, never()).analyzeImage(any());
        }
    }
}
