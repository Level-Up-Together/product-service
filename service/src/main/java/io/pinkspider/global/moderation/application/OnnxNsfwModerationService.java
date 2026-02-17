package io.pinkspider.global.moderation.application;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.pinkspider.global.moderation.config.ModerationProperties;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import io.pinkspider.global.moderation.domain.dto.ModerationLabel;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * ONNX Runtime 기반 NSFW 이미지 검증 서비스
 *
 * OpenNSFW2 모델을 사용하여 로컬에서 NSFW 이미지를 감지합니다.
 * AWS Rekognition 대비 $0 비용으로 동작하며, CPU 기반 추론을 수행합니다.
 *
 * 모델 입력: 224x224 RGB 이미지 (NCHW 포맷, 정규화됨)
 * 모델 출력: [sfw_score, nsfw_score] (합계 = 1.0)
 */
@Slf4j
public class OnnxNsfwModerationService implements ImageModerationService {

    private static final int IMAGE_SIZE = 224;
    private static final String NSFW_CATEGORY = "NSFW";

    private final ModerationProperties properties;
    private final OrtEnvironment env;
    private final OrtSession session;

    public OnnxNsfwModerationService(ModerationProperties properties) {
        this.properties = properties;
        this.env = OrtEnvironment.getEnvironment();

        try {
            String modelPath = properties.getOnnx().getModelPath();
            byte[] modelBytes = loadModelBytes(modelPath);
            this.session = env.createSession(modelBytes, new OrtSession.SessionOptions());
            log.info("OnnxNsfwModerationService 초기화 완료 - 모델: {}, NSFW 임계값: {}",
                modelPath, properties.getOnnx().getNsfwThreshold());
        } catch (OrtException | IOException e) {
            throw new IllegalStateException("ONNX NSFW 모델 로드 실패: " + e.getMessage(), e);
        }
    }

    // 테스트용 생성자
    OnnxNsfwModerationService(ModerationProperties properties, OrtEnvironment env, OrtSession session) {
        this.properties = properties;
        this.env = env;
        this.session = session;
    }

    @Override
    public ImageModerationResult analyzeImage(MultipartFile imageFile) {
        log.debug("ONNX NSFW 이미지 분석 시작: 파일={}, 크기={} bytes",
            imageFile.getOriginalFilename(), imageFile.getSize());

        try {
            BufferedImage image = ImageIO.read(imageFile.getInputStream());
            if (image == null) {
                log.warn("이미지 파일을 읽을 수 없습니다: {}", imageFile.getOriginalFilename());
                return ImageModerationResult.safe();
            }
            return runInference(image);
        } catch (IOException e) {
            log.error("이미지 파일 읽기 실패: {}", e.getMessage());
            return ImageModerationResult.safe();
        }
    }

    @Override
    public ImageModerationResult analyzeImageUrl(String imageUrl) {
        log.warn("ONNX NSFW: URL 기반 이미지 분석은 지원하지 않습니다. URL={}", imageUrl);
        return ImageModerationResult.safe();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "onnx-nsfw";
    }

    /**
     * ONNX 모델 추론 실행
     */
    ImageModerationResult runInference(BufferedImage image) {
        try {
            float[][][][] inputTensor = preprocessImage(image);

            try (OnnxTensor tensor = OnnxTensor.createTensor(env, inputTensor)) {
                OrtSession.Result result = session.run(Map.of(session.getInputNames().iterator().next(), tensor));
                float[][] output = (float[][]) result.get(0).getValue();

                float nsfwScore = output[0][1]; // index 1 = NSFW score
                float sfwScore = output[0][0];  // index 0 = SFW score
                float nsfwThreshold = properties.getOnnx().getNsfwThreshold();

                log.debug("ONNX NSFW 분석 완료: SFW={}, NSFW={}, 임계값={}",
                    String.format("%.4f", sfwScore),
                    String.format("%.4f", nsfwScore),
                    nsfwThreshold);

                if (nsfwScore >= nsfwThreshold) {
                    double confidencePercent = nsfwScore * 100.0;
                    ModerationLabel label = ModerationLabel.builder()
                        .category(NSFW_CATEGORY)
                        .name("NSFW Content")
                        .confidence(confidencePercent)
                        .build();

                    return ImageModerationResult.unsafe(
                        "부적절한 콘텐츠가 감지되었습니다 (NSFW: " + String.format("%.1f", confidencePercent) + "%)",
                        List.of(label),
                        Map.of(NSFW_CATEGORY, confidencePercent),
                        getProviderName()
                    );
                }

                return ImageModerationResult.builder()
                    .safe(true)
                    .overallConfidence((1.0 - nsfwScore) * 100.0)
                    .detectedLabels(List.of())
                    .categoryScores(Map.of(NSFW_CATEGORY, (double) nsfwScore * 100.0))
                    .provider(getProviderName())
                    .build();
            }
        } catch (Exception e) {
            log.error("ONNX 추론 실패: {}", e.getMessage());
            return ImageModerationResult.safe();
        }
    }

    /**
     * 이미지 전처리: 224x224 리사이즈 + NCHW 정규화
     * OpenNSFW2 모델 기준: BGR 채널 순서, ImageNet mean 차감
     */
    float[][][][] preprocessImage(BufferedImage original) {
        BufferedImage resized = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(original, 0, 0, IMAGE_SIZE, IMAGE_SIZE, null);
        g.dispose();

        // NCHW format: [batch=1][channels=3][height=224][width=224]
        float[][][][] tensor = new float[1][3][IMAGE_SIZE][IMAGE_SIZE];

        // ImageNet mean values (BGR order for OpenNSFW2)
        float[] mean = {104.0f, 117.0f, 123.0f};

        for (int y = 0; y < IMAGE_SIZE; y++) {
            for (int x = 0; x < IMAGE_SIZE; x++) {
                int rgb = resized.getRGB(x, y);
                float r = (float) ((rgb >> 16) & 0xFF);
                float g2 = (float) ((rgb >> 8) & 0xFF);
                float b = (float) (rgb & 0xFF);

                // BGR order with mean subtraction
                tensor[0][0][y][x] = b - mean[0];
                tensor[0][1][y][x] = g2 - mean[1];
                tensor[0][2][y][x] = r - mean[2];
            }
        }

        return tensor;
    }

    private byte[] loadModelBytes(String modelPath) throws IOException {
        Resource resource = new DefaultResourceLoader().getResource(modelPath);
        try (InputStream is = resource.getInputStream()) {
            return is.readAllBytes();
        }
    }
}
