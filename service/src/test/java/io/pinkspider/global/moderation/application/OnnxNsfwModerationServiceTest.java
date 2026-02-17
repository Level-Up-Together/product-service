package io.pinkspider.global.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import io.pinkspider.global.moderation.config.ModerationProperties;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
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
    @DisplayName("이미지 전처리 테스트")
    class PreprocessImageTest {

        @Test
        @DisplayName("이미지를 224x224 NCHW 텐서로 변환한다")
        void shouldPreprocessImageToCorrectDimensions() {
            // given
            ModerationProperties properties = createProperties();
            OrtEnvironment env = mock(OrtEnvironment.class);
            OrtSession session = mock(OrtSession.class);

            OnnxNsfwModerationService service = new OnnxNsfwModerationService(properties, env, session);
            BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

            // when
            float[][][][] tensor = service.preprocessImage(testImage);

            // then
            assertThat(tensor.length).isEqualTo(1);       // batch
            assertThat(tensor[0].length).isEqualTo(3);     // channels
            assertThat(tensor[0][0].length).isEqualTo(224); // height
            assertThat(tensor[0][0][0].length).isEqualTo(224); // width
        }
    }

    @Nested
    @DisplayName("analyzeImage 테스트")
    class AnalyzeImageTest {

        @Test
        @DisplayName("이미지를 읽을 수 없으면 안전한 결과를 반환한다")
        void shouldReturnSafeWhenCannotReadImage() throws IOException {
            // given
            ModerationProperties properties = createProperties();
            OrtEnvironment env = mock(OrtEnvironment.class);
            OrtSession session = mock(OrtSession.class);

            OnnxNsfwModerationService service = new OnnxNsfwModerationService(properties, env, session);

            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getOriginalFilename()).thenReturn("invalid.txt");
            when(mockFile.getSize()).thenReturn(100L);
            // Return invalid image data that ImageIO can't parse
            when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{0, 1, 2, 3}));

            // when
            ImageModerationResult result = service.analyzeImage(mockFile);

            // then
            assertThat(result.isSafe()).isTrue();
        }

        @Test
        @DisplayName("IOException 발생 시 안전한 결과를 반환한다")
        void shouldReturnSafeOnIOException() throws IOException {
            // given
            ModerationProperties properties = createProperties();
            OrtEnvironment env = mock(OrtEnvironment.class);
            OrtSession session = mock(OrtSession.class);

            OnnxNsfwModerationService service = new OnnxNsfwModerationService(properties, env, session);

            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getOriginalFilename()).thenReturn("test.jpg");
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getInputStream()).thenThrow(new IOException("파일 읽기 실패"));

            // when
            ImageModerationResult result = service.analyzeImage(mockFile);

            // then
            assertThat(result.isSafe()).isTrue();
        }
    }

    @Nested
    @DisplayName("runInference 테스트")
    class RunInferenceTest {

        @Test
        @DisplayName("세션이 null이면 안전한 결과를 반환한다 (OrtException 핸들링)")
        void shouldReturnSafeOnOrtException() {
            // given — OrtSession이 null이면 NullPointerException → catch 블록에서 안전 처리
            // OrtSession은 final 메서드가 있어 mock 불가, 대신 실제 실패 시나리오 테스트
            ModerationProperties properties = createProperties();
            OrtEnvironment env = OrtEnvironment.getEnvironment();

            // null session을 사용하여 OrtException 유발
            OnnxNsfwModerationService service = new OnnxNsfwModerationService(properties, env, null);
            BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

            // when
            ImageModerationResult result = service.runInference(testImage);

            // then
            assertThat(result.isSafe()).isTrue();
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
