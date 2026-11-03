package io.pinkspider.global.moderation.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.image.ImageContentValidator;
import io.pinkspider.global.image.InvalidImageException;
import io.pinkspider.global.moderation.annotation.ModerateImage;
import io.pinkspider.global.moderation.application.ImageModerationMetrics;
import io.pinkspider.global.moderation.application.ImageModerationService;
import io.pinkspider.global.moderation.config.ModerationProperties;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import io.pinkspider.global.moderation.domain.dto.ModerationLabel;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageModerationAspect 테스트")
class ImageModerationAspectTest {

    private static final String PROVIDER = "onnx-nsfw";

    @Mock private ImageModerationService imageModerationService;
    @Mock private ImageModerationMetrics metrics;
    @Mock private ImageContentValidator imageContentValidator;

    private ModerationProperties properties;
    private ImageModerationAspect aspect;

    @BeforeEach
    void setUp() {
        properties = new ModerationProperties();
        properties.setProvider(PROVIDER);
        aspect =
                new ImageModerationAspect(
                        imageModerationService, properties, metrics, imageContentValidator);
        lenient().when(imageModerationService.isEnabled()).thenReturn(true);
        lenient().when(imageModerationService.getProviderName()).thenReturn(PROVIDER);
    }

    private static ProceedingJoinPoint joinPoint(Object... args) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        lenient().when(joinPoint.getArgs()).thenReturn(args);
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.toShortString()).thenReturn("TestService.upload(..)");
        lenient().when(joinPoint.proceed()).thenReturn("result");
        return joinPoint;
    }

    private static MultipartFile file(String name) {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.isEmpty()).thenReturn(false);
        lenient().when(file.getOriginalFilename()).thenReturn(name);
        return file;
    }

    private static MultipartFile emptyFile() {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.isEmpty()).thenReturn(true);
        return file;
    }

    private static ImageModerationResult unsafe() {
        return ImageModerationResult.unsafe(
                "NSFW 콘텐츠 감지",
                List.of(
                        ModerationLabel.builder()
                                .category("NSFW")
                                .name("NSFW Content")
                                .confidence(95.0)
                                .build()),
                Map.of("NSFW", 95.0),
                PROVIDER);
    }

    @Nested
    @DisplayName("모더레이션 비활성화 시")
    class WhenDisabled {

        @Test
        @DisplayName("모더레이션이 비활성화되면 검증 없이 원본 메서드를 실행한다")
        void shouldProceedWithoutModeration() throws Throwable {
            when(imageModerationService.isEnabled()).thenReturn(false);
            ProceedingJoinPoint joinPoint = joinPoint("userId", file("a.jpg"));

            Object result = aspect.moderateImage(joinPoint, mock(ModerateImage.class));

            assertThat(result).isEqualTo("result");
            verify(imageModerationService, never()).analyzeImage(any());
            verifyNoInteractions(metrics);
        }
    }

    @Nested
    @DisplayName("단일 MultipartFile 인자")
    class SingleFile {

        @Test
        @DisplayName("안전한 이미지면 원본 메서드를 실행하고 safe 카운터를 올린다")
        void shouldProceedWhenImageIsSafe() throws Throwable {
            MultipartFile imageFile = file("a.jpg");
            when(imageModerationService.analyzeImage(imageFile))
                    .thenReturn(ImageModerationResult.safe());
            ProceedingJoinPoint joinPoint = joinPoint("userId", imageFile);

            Object result = aspect.moderateImage(joinPoint, mock(ModerateImage.class));

            assertThat(result).isEqualTo("result");
            verify(imageModerationService).analyzeImage(imageFile);
            verify(metrics).checked(PROVIDER, ImageModerationMetrics.RESULT_SAFE);
            verify(metrics, never()).failOpen(any(), any());
        }

        @Test
        @DisplayName("부적절한 이미지면 000010 CustomException 을 던지고 blocked 카운터를 올린다")
        void shouldThrowExceptionWhenImageIsUnsafe() throws Throwable {
            MultipartFile imageFile = file("a.jpg");
            when(imageModerationService.analyzeImage(imageFile)).thenReturn(unsafe());
            ProceedingJoinPoint joinPoint = joinPoint("userId", imageFile);

            assertThatThrownBy(() -> aspect.moderateImage(joinPoint, mock(ModerateImage.class)))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("error.moderation.inappropriate_image");

            verify(joinPoint, never()).proceed();
            verify(metrics).checked(PROVIDER, ImageModerationMetrics.RESULT_BLOCKED);
        }

        @Test
        @DisplayName("빈 이미지 파일은 검증을 건너뛴다")
        void shouldSkipEmptyFile() throws Throwable {
            ProceedingJoinPoint joinPoint = joinPoint("userId", emptyFile());

            Object result = aspect.moderateImage(joinPoint, mock(ModerateImage.class));

            assertThat(result).isEqualTo("result");
            verify(imageModerationService, never()).analyzeImage(any());
            verifyNoInteractions(metrics);
        }

        @Test
        @DisplayName("MultipartFile 파라미터가 없으면 검증 없이 실행한다")
        void shouldProceedWhenNoMultipartFile() throws Throwable {
            ProceedingJoinPoint joinPoint = joinPoint("userId", 123L, List.of("not-a-file"));

            Object result = aspect.moderateImage(joinPoint, mock(ModerateImage.class));

            assertThat(result).isEqualTo("result");
            verify(imageModerationService, never()).analyzeImage(any());
        }
    }

    @Nested
    @DisplayName("여러 파일 인자 — List·배열 (LUT-650)")
    class MultipleFiles {

        @Test
        @DisplayName("List<MultipartFile> 의 파일을 전부 검사한다")
        void shouldAnalyzeEveryFileInList() throws Throwable {
            MultipartFile a = file("a.jpg");
            MultipartFile b = file("b.jpg");
            MultipartFile c = file("c.jpg");
            when(imageModerationService.analyzeImage(any()))
                    .thenReturn(ImageModerationResult.safe());
            ProceedingJoinPoint joinPoint =
                    joinPoint(1L, "userId", "2026-11-02", List.of(a, b, c), null);

            Object result = aspect.moderateImage(joinPoint, mock(ModerateImage.class));

            assertThat(result).isEqualTo("result");
            verify(imageModerationService).analyzeImage(a);
            verify(imageModerationService).analyzeImage(b);
            verify(imageModerationService).analyzeImage(c);
            verify(metrics, times(3)).checked(PROVIDER, ImageModerationMetrics.RESULT_SAFE);
        }

        @Test
        @DisplayName("MultipartFile[] 배열도 전부 검사한다")
        void shouldAnalyzeEveryFileInArray() throws Throwable {
            MultipartFile a = file("a.jpg");
            MultipartFile b = file("b.jpg");
            when(imageModerationService.analyzeImage(any()))
                    .thenReturn(ImageModerationResult.safe());
            // (Object) 캐스팅: 배열이 가변 인자로 풀리지 않고 인자 하나(MultipartFile[])로 들어가야 배열 분기를 탄다
            ProceedingJoinPoint joinPoint = joinPoint((Object) new MultipartFile[] {a, b});

            aspect.moderateImage(joinPoint, mock(ModerateImage.class));

            verify(imageModerationService, times(2)).analyzeImage(any());
            verify(joinPoint).proceed();
        }

        @Test
        @DisplayName("리스트 중 한 장만 부적절해도 차단하고, 그 뒤 파일은 검사하지 않는다")
        void shouldBlockWhenAnyFileIsUnsafe() throws Throwable {
            MultipartFile a = file("a.jpg");
            MultipartFile b = file("b.jpg");
            MultipartFile c = file("c.jpg");
            when(imageModerationService.analyzeImage(a)).thenReturn(ImageModerationResult.safe());
            when(imageModerationService.analyzeImage(b)).thenReturn(unsafe());
            ProceedingJoinPoint joinPoint = joinPoint(List.of(a, b, c));

            assertThatThrownBy(() -> aspect.moderateImage(joinPoint, mock(ModerateImage.class)))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("error.moderation.inappropriate_image");

            verify(joinPoint, never()).proceed();
            verify(imageModerationService, never()).analyzeImage(c);
            verify(metrics).checked(PROVIDER, ImageModerationMetrics.RESULT_SAFE);
            verify(metrics).checked(PROVIDER, ImageModerationMetrics.RESULT_BLOCKED);
        }

        @Test
        @DisplayName("리스트 안의 빈 파일·null 은 건너뛴다")
        void shouldSkipEmptyAndNullElementsInList() throws Throwable {
            MultipartFile a = file("a.jpg");
            when(imageModerationService.analyzeImage(a)).thenReturn(ImageModerationResult.safe());
            ProceedingJoinPoint joinPoint = joinPoint(Arrays.asList(emptyFile(), null, a));

            aspect.moderateImage(joinPoint, mock(ModerateImage.class));

            verify(imageModerationService, times(1)).analyzeImage(any());
            verify(joinPoint).proceed();
        }

        @Test
        @DisplayName("collectImageFiles 는 단일·배열·컬렉션을 인자 순서대로 모으고 파일이 아닌 값은 무시한다")
        void collectImageFilesKeepsOrder() {
            MultipartFile a = file("a.jpg");
            MultipartFile b = file("b.jpg");
            MultipartFile c = file("c.jpg");
            MultipartFile d = file("d.jpg");

            List<MultipartFile> files =
                    ImageModerationAspect.collectImageFiles(
                            new Object[] {
                                "userId",
                                a,
                                new MultipartFile[] {b, null},
                                List.of("x", c),
                                emptyFile(),
                                List.of(d)
                            });

            assertThat(files).containsExactly(a, b, c, d);
            assertThat(ImageModerationAspect.collectImageFiles(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("검수 실패 정책 — moderation.image.fail-open (LUT-650)")
    class FailurePolicy {

        @Test
        @DisplayName("fail-open(기본)이면 실패한 파일은 통과시키고 fail_open 카운터·error 로그를 남긴다")
        void shouldProceedOnFailureWhenFailOpen() throws Throwable {
            MultipartFile a = file("a.webp");
            MultipartFile b = file("b.jpg");
            when(imageModerationService.analyzeImage(a))
                    .thenReturn(
                            ImageModerationResult.failOpen(
                                    ImageModerationResult.REASON_UNREADABLE, PROVIDER));
            when(imageModerationService.analyzeImage(b)).thenReturn(ImageModerationResult.safe());
            ProceedingJoinPoint joinPoint = joinPoint(List.of(a, b));

            Object result = aspect.moderateImage(joinPoint, mock(ModerateImage.class));

            assertThat(result).isEqualTo("result");
            verify(metrics).failOpen(PROVIDER, ImageModerationResult.REASON_UNREADABLE);
            verify(metrics).checked(PROVIDER, ImageModerationMetrics.RESULT_SAFE);
            verify(metrics, never()).checked(PROVIDER, ImageModerationMetrics.RESULT_BLOCKED);
        }

        @Test
        @DisplayName("fail-open=false 면 실패한 파일에서 000011 로 차단한다 (카운터는 그래도 올린다)")
        void shouldBlockOnFailureWhenFailClosed() throws Throwable {
            properties.setFailOpen(false);
            MultipartFile a = file("a.jpg");
            when(imageModerationService.analyzeImage(a))
                    .thenReturn(
                            ImageModerationResult.failOpen(
                                    ImageModerationResult.REASON_READ_ERROR, PROVIDER));
            ProceedingJoinPoint joinPoint = joinPoint(a);

            assertThatThrownBy(() -> aspect.moderateImage(joinPoint, mock(ModerateImage.class)))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("error.moderation.unavailable");

            verify(joinPoint, never()).proceed();
            verify(metrics).failOpen(PROVIDER, ImageModerationResult.REASON_READ_ERROR);
        }

        @Test
        @DisplayName("제공자가 런타임 예외를 던지면 reason=exception 인 실패로 다뤄 정책을 적용한다")
        void shouldTreatRuntimeExceptionAsFailure() throws Throwable {
            MultipartFile a = file("a.jpg");
            when(imageModerationService.analyzeImage(a))
                    .thenThrow(new IllegalStateException("model not loaded"));
            ProceedingJoinPoint joinPoint = joinPoint(a);

            Object result = aspect.moderateImage(joinPoint, mock(ModerateImage.class));

            assertThat(result).isEqualTo("result");
            verify(metrics).failOpen(PROVIDER, ImageModerationResult.REASON_EXCEPTION);

            properties.setFailOpen(false);
            assertThatThrownBy(() -> aspect.moderateImage(joinPoint, mock(ModerateImage.class)))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("error.moderation.unavailable");
        }

        @Test
        @DisplayName("제공자가 null 을 돌려줘도 reason=exception 인 실패로 다룬다")
        void shouldTreatNullResultAsFailure() throws Throwable {
            MultipartFile a = file("a.jpg");
            when(imageModerationService.analyzeImage(a)).thenReturn(null);
            ProceedingJoinPoint joinPoint = joinPoint(a);

            Object result = aspect.moderateImage(joinPoint, mock(ModerateImage.class));

            assertThat(result).isEqualTo("result");
            verify(metrics).failOpen(PROVIDER, ImageModerationResult.REASON_EXCEPTION);
        }
    }

    @Nested
    @DisplayName("이미지 형식 검증 (LUT-710)")
    class ContentValidation {
        @Test
        @DisplayName("검증기가 거절하면 검수를 부르지 않고 InvalidImageException 이 그대로 올라간다")
        void rejectedBeforeModeration() throws Throwable {
            MultipartFile file =
                    new MockMultipartFile("image", "fake.png", "image/png", "not png".getBytes());
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            when(joinPoint.getArgs()).thenReturn(new Object[] {file});
            doThrow(InvalidImageException.notImage()).when(imageContentValidator).validate(file);

            assertThatThrownBy(() -> aspect.moderateImage(joinPoint, mock(ModerateImage.class)))
                    .isInstanceOf(InvalidImageException.class);

            verify(imageModerationService, never()).analyzeImage(any());
            verify(joinPoint, never()).proceed();
        }

        @Test
        @DisplayName("검수 제공자가 꺼져 있어도 형식 검증은 한다")
        void validatesEvenWhenModerationDisabled() throws Throwable {
            when(imageModerationService.isEnabled()).thenReturn(false);
            MultipartFile file =
                    new MockMultipartFile("image", "a.png", "image/png", "x".getBytes());
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            when(joinPoint.getArgs()).thenReturn(new Object[] {file});
            when(joinPoint.proceed()).thenReturn("ok");

            Object result = aspect.moderateImage(joinPoint, mock(ModerateImage.class));

            assertThat(result).isEqualTo("ok");
            verify(imageContentValidator).validate(file);
            verify(imageModerationService, never()).analyzeImage(any());
        }
    }
}
