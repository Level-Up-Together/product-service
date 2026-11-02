package io.pinkspider.global.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.pinkspider.global.moderation.config.ModerationProperties;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("OnnxNsfwModerationService 테스트")
class OnnxNsfwModerationServiceTest {

    @Test
    @DisplayName("isEnabled()는 항상 true를 반환한다")
    void isEnabled_shouldReturnTrue() {
        // given
        ModerationProperties properties = createProperties();
        OrtEnvironment env = mock(OrtEnvironment.class);
        OrtSession session = mock(OrtSession.class);

        OnnxNsfwModerationService service = new OnnxNsfwModerationService(properties, env, session);

        // when & then
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("getProviderName()은 'onnx-nsfw'를 반환한다")
    void getProviderName_shouldReturnOnnxNsfw() {
        // given
        ModerationProperties properties = createProperties();
        OrtEnvironment env = mock(OrtEnvironment.class);
        OrtSession session = mock(OrtSession.class);

        OnnxNsfwModerationService service = new OnnxNsfwModerationService(properties, env, session);

        // when & then
        assertThat(service.getProviderName()).isEqualTo("onnx-nsfw");
    }

    @Test
    @DisplayName("analyzeImageUrl()은 항상 안전한 결과를 반환한다")
    void analyzeImageUrl_shouldReturnSafe() {
        // given
        ModerationProperties properties = createProperties();
        OrtEnvironment env = mock(OrtEnvironment.class);
        OrtSession session = mock(OrtSession.class);

        OnnxNsfwModerationService service = new OnnxNsfwModerationService(properties, env, session);

        // when
        ImageModerationResult result = service.analyzeImageUrl("https://example.com/image.jpg");

        // then
        assertThat(result.isSafe()).isTrue();
    }

    @Nested
    @DisplayName("이미지 전처리 테스트 (LUT-651: 동봉 모델 NHWC, 256 리사이즈 → 가운데 224 자르기)")
    class PreprocessImageTest {

        @Test
        @DisplayName("기본(동봉 모델)은 224x224 NHWC 텐서 [1][224][224][3] 로 변환한다")
        void shouldPreprocessToNhwcByDefault() {
            OnnxNsfwModerationService service =
                    new OnnxNsfwModerationService(
                            createProperties(), mock(OrtEnvironment.class), mock(OrtSession.class));
            BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

            float[][][][] tensor = service.preprocessImage(testImage);

            assertThat(service.getInputLayout()).isEqualTo("NHWC");
            assertThat(tensor.length).isEqualTo(1); // batch
            assertThat(tensor[0].length).isEqualTo(224); // height
            assertThat(tensor[0][0].length).isEqualTo(224); // width
            assertThat(tensor[0][0][0].length).isEqualTo(3); // channels
        }

        @Test
        @DisplayName("NCHW 모델이면 [1][3][224][224] 로 변환한다")
        void shouldPreprocessToNchwWhenChannelsFirst() {
            OnnxNsfwModerationService service =
                    new OnnxNsfwModerationService(
                            createProperties(),
                            mock(OrtEnvironment.class),
                            mock(OrtSession.class),
                            true);
            BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

            float[][][][] tensor = service.preprocessImage(testImage);

            assertThat(service.getInputLayout()).isEqualTo("NCHW");
            assertThat(tensor[0].length).isEqualTo(3);
            assertThat(tensor[0][0].length).isEqualTo(224);
            assertThat(tensor[0][0][0].length).isEqualTo(224);
        }

        @Test
        @DisplayName("단색 이미지는 BGR 순서로 평균(104,117,123)을 뺀 값이 모든 픽셀에 들어간다")
        void shouldSubtractMeanInBgrOrder() {
            OnnxNsfwModerationService nhwc =
                    new OnnxNsfwModerationService(
                            createProperties(), mock(OrtEnvironment.class), mock(OrtSession.class));
            OnnxNsfwModerationService nchw =
                    new OnnxNsfwModerationService(
                            createProperties(),
                            mock(OrtEnvironment.class),
                            mock(OrtSession.class),
                            true);
            BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = image.createGraphics();
            g.setColor(new java.awt.Color(200, 150, 50)); // R=200, G=150, B=50
            g.fillRect(0, 0, 300, 200);
            g.dispose();

            float[][][][] t1 = nhwc.preprocessImage(image);
            float[][][][] t2 = nchw.preprocessImage(image);

            for (int[] p : new int[][] {{0, 0}, {111, 57}, {223, 223}}) {
                assertThat(t1[0][p[0]][p[1]]).containsExactly(50 - 104f, 150 - 117f, 200 - 123f);
                assertThat(t2[0][0][p[0]][p[1]]).isEqualTo(50 - 104f);
                assertThat(t2[0][1][p[0]][p[1]]).isEqualTo(150 - 117f);
                assertThat(t2[0][2][p[0]][p[1]]).isEqualTo(200 - 123f);
            }
        }

        @Test
        @DisplayName("가운데 자르기: 256 리사이즈 뒤 바깥 16px 테두리는 텐서에 들어가지 않는다")
        void shouldCenterCrop() {
            OnnxNsfwModerationService service =
                    new OnnxNsfwModerationService(
                            createProperties(), mock(OrtEnvironment.class), mock(OrtSession.class));
            // 256x256 원본: 바깥 16px 는 흰색, 안쪽 224x224 는 검정 → 리사이즈 없이 그대로 잘린다
            BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = image.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, 256, 256);
            g.setColor(java.awt.Color.BLACK);
            g.fillRect(16, 16, 224, 224);
            g.dispose();

            float[][][][] t = service.preprocessImage(image);

            // 검정(0,0,0) - 평균 → (-104, -117, -123); 흰색이 섞였다면 양수가 나온다
            assertThat(t[0][0][0]).containsExactly(-104f, -117f, -123f);
            assertThat(t[0][223][223]).containsExactly(-104f, -117f, -123f);
        }

        @Test
        @DisplayName("isChannelsFirst: [N,3,H,W] 만 NCHW, [N,H,W,3]·그 외는 NHWC")
        void isChannelsFirstShapes() {
            assertThat(OnnxNsfwModerationService.isChannelsFirst(new long[] {1, 3, 224, 224}))
                    .isTrue();
            assertThat(OnnxNsfwModerationService.isChannelsFirst(new long[] {-1, 3, 224, 224}))
                    .isTrue();
            assertThat(OnnxNsfwModerationService.isChannelsFirst(new long[] {-1, 224, 224, 3}))
                    .isFalse();
            assertThat(OnnxNsfwModerationService.isChannelsFirst(new long[] {1, 224, 224}))
                    .isFalse();
            assertThat(OnnxNsfwModerationService.isChannelsFirst(null)).isFalse();
        }

        @Test
        @DisplayName("detectChannelsFirst: 세션 입력 정보를 못 읽으면(null·예외) NHWC 로 본다")
        void detectFallsBackToNhwc() throws OrtException {
            OrtSession nullInfo = mock(OrtSession.class);
            when(nullInfo.getInputInfo()).thenReturn(null);
            OrtSession throwing = mock(OrtSession.class);
            when(throwing.getInputInfo()).thenThrow(new IllegalStateException("closed"));

            assertThat(OnnxNsfwModerationService.detectChannelsFirst(nullInfo)).isFalse();
            assertThat(OnnxNsfwModerationService.detectChannelsFirst(throwing)).isFalse();
            assertThat(OnnxNsfwModerationService.detectChannelsFirst(mock(OrtSession.class)))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("analyzeImage 테스트")
    class AnalyzeImageTest {

        @Test
        @DisplayName("이미지를 읽을 수 없으면 fail-open 결과(unreadable)를 반환한다 (LUT-650)")
        void shouldReturnSafeWhenCannotReadImage() throws IOException {
            // given
            ModerationProperties properties = createProperties();
            OrtEnvironment env = mock(OrtEnvironment.class);
            OrtSession session = mock(OrtSession.class);

            OnnxNsfwModerationService service =
                    new OnnxNsfwModerationService(properties, env, session);

            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getOriginalFilename()).thenReturn("invalid.txt");
            when(mockFile.getSize()).thenReturn(100L);
            // Return invalid image data that ImageIO can't parse
            when(mockFile.getInputStream())
                    .thenReturn(new ByteArrayInputStream(new byte[] {0, 1, 2, 3}));

            // when
            ImageModerationResult result = service.analyzeImage(mockFile);

            // then
            assertThat(result.isSafe()).isTrue();
            assertThat(result.isFailOpen()).isTrue();
            assertThat(result.getFailureReason())
                    .isEqualTo(ImageModerationResult.REASON_UNREADABLE);
            assertThat(result.getProvider()).isEqualTo("onnx-nsfw");
        }

        @Test
        @DisplayName("IOException 발생 시 fail-open 결과(read_error)를 반환한다 (LUT-650)")
        void shouldReturnSafeOnIOException() throws IOException {
            // given
            ModerationProperties properties = createProperties();
            OrtEnvironment env = mock(OrtEnvironment.class);
            OrtSession session = mock(OrtSession.class);

            OnnxNsfwModerationService service =
                    new OnnxNsfwModerationService(properties, env, session);

            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getOriginalFilename()).thenReturn("test.jpg");
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getInputStream()).thenThrow(new IOException("파일 읽기 실패"));

            // when
            ImageModerationResult result = service.analyzeImage(mockFile);

            // then
            assertThat(result.isSafe()).isTrue();
            assertThat(result.isFailOpen()).isTrue();
            assertThat(result.getFailureReason())
                    .isEqualTo(ImageModerationResult.REASON_READ_ERROR);
        }
    }

    @Nested
    @DisplayName("runInference 테스트")
    class RunInferenceTest {

        @Test
        @DisplayName("세션이 null이면 fail-open 결과(inference_error)를 반환한다 (LUT-650)")
        void shouldReturnSafeOnOrtException() {
            // given — OrtSession이 null이면 NullPointerException → catch 블록에서 안전 처리
            // OrtSession은 final 메서드가 있어 mock 불가, 대신 실제 실패 시나리오 테스트
            ModerationProperties properties = createProperties();
            OrtEnvironment env = OrtEnvironment.getEnvironment();

            // null session을 사용하여 OrtException 유발
            OnnxNsfwModerationService service =
                    new OnnxNsfwModerationService(properties, env, null);
            BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

            // when
            ImageModerationResult result = service.runInference(testImage);

            // then
            assertThat(result.isSafe()).isTrue();
            assertThat(result.isFailOpen()).isTrue();
            assertThat(result.getFailureReason())
                    .isEqualTo(ImageModerationResult.REASON_INFERENCE_ERROR);
        }
    }

    private ModerationProperties createProperties() {
        ModerationProperties properties = new ModerationProperties();
        properties.setProvider("onnx-nsfw");
        ModerationProperties.OnnxConfig onnxConfig = new ModerationProperties.OnnxConfig();
        onnxConfig.setModelPath("classpath:models/nsfw.onnx");
        onnxConfig.setNsfwThreshold(0.8f);
        properties.setOnnx(onnxConfig);
        return properties;
    }
}
