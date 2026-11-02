package io.pinkspider.leveluptogethermvp.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.moderation.application.OnnxNsfwModerationService;
import io.pinkspider.global.moderation.config.ModerationProperties;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * LUT-651: 동봉 모델({@code classpath:models/nsfw.onnx})이 이 런타임(onnxruntime 1.17.3)에서 실제로 로드·추론되는지 스모크.
 * 모델 파일을 바꾸거나 전처리를 고치면 여기서 먼저 깨진다. 네이티브 라이브러리는 jar 동봉(linux-x64 · linux-aarch64 · osx).
 */
@DisplayName("동봉 ONNX NSFW 모델 스모크 (LUT-651)")
class OnnxNsfwModelSmokeTest {

    @Test
    @DisplayName("기본 설정(classpath:models/nsfw.onnx)으로 로드되고 입력 레이아웃은 NHWC 다")
    void loadsBundledModel() {
        ModerationProperties properties = new ModerationProperties();
        properties.setProvider("onnx-nsfw");

        OnnxNsfwModerationService service = new OnnxNsfwModerationService(properties);

        assertThat(service.isEnabled()).isTrue();
        assertThat(service.getProviderName()).isEqualTo("onnx-nsfw");
        assertThat(service.getInputLayout()).isEqualTo("NHWC");
    }

    @Test
    @DisplayName("평범한 그라데이션 PNG 는 판정 완료(failOpen 아님)·안전이며 NSFW 점수가 임계값보다 훨씬 낮다")
    void classifiesPlainImageAsSafe() throws Exception {
        ModerationProperties properties = new ModerationProperties();
        properties.setProvider("onnx-nsfw");
        OnnxNsfwModerationService service = new OnnxNsfwModerationService(properties);

        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(
                new GradientPaint(
                        0, 0, new Color(30, 90, 200), 640, 480, new Color(240, 230, 120)));
        g.fillRect(0, 0, 640, 480);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        MockMultipartFile file =
                new MockMultipartFile("image", "plain.png", "image/png", png.toByteArray());

        ImageModerationResult result = service.analyzeImage(file);

        assertThat(result.isFailOpen()).isFalse();
        assertThat(result.isSafe()).isTrue();
        assertThat(result.getProvider()).isEqualTo("onnx-nsfw");
        assertThat(result.getCategoryScores()).containsKey("NSFW");
        assertThat(result.getCategoryScores().get("NSFW")).isLessThan(50.0);
    }

    @Test
    @DisplayName("이미지가 아닌 바이트는 fail-open(unreadable) 결과다")
    void unreadableBytesAreFailOpen() {
        ModerationProperties properties = new ModerationProperties();
        properties.setProvider("onnx-nsfw");
        OnnxNsfwModerationService service = new OnnxNsfwModerationService(properties);
        MockMultipartFile file =
                new MockMultipartFile("image", "x.webp", "image/webp", new byte[] {1, 2, 3, 4});

        ImageModerationResult result = service.analyzeImage(file);

        assertThat(result.isFailOpen()).isTrue();
        assertThat(result.getFailureReason()).isEqualTo(ImageModerationResult.REASON_UNREADABLE);
    }
}
