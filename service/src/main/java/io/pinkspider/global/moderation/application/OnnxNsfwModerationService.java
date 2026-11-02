package io.pinkspider.global.moderation.application;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import io.pinkspider.global.moderation.config.ModerationProperties;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import io.pinkspider.global.moderation.domain.dto.ModerationLabel;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * ONNX Runtime 기반 NSFW 이미지 검증 서비스.
 *
 * <p>Yahoo open_nsfw(ResNet-50) 의 ONNX 변환본({@code app/src/main/resources/models/nsfw.onnx},
 * 출처·라이선스·SHA-256 은 같은 폴더 README)을 로컬 CPU 로 추론한다 — AWS Rekognition 대비 $0.
 *
 * <p>모델 입력은 224×224, BGR 순서, 평균 {@code [104, 117, 123]} 차감(0~255 스케일). 레이아웃은 기동 시 세션 입력 모양으로 자동
 * 감지한다 — 동봉 모델은 NHWC {@code [N,224,224,3]} 이고 {@code [N,3,224,224]} 면 NCHW 로 채운다(LUT-651 — 예전 코드는
 * NCHW 고정이라 동봉 모델에서 추론이 항상 실패했다). 전처리는 참조 구현(opennsfw-standalone)과 같이 256 리사이즈 → 가운데 224 자르기이며 JPEG
 * 재인코딩만 생략한다. 출력은 {@code [sfw, nsfw]} (softmax, 합 1).
 */
@Slf4j
public class OnnxNsfwModerationService implements ImageModerationService {

    private static final int IMAGE_SIZE = 224;
    private static final int RESIZE_SIZE = 256;
    private static final int CROP_OFFSET = (RESIZE_SIZE - IMAGE_SIZE) / 2;
    private static final String NSFW_CATEGORY = "NSFW";

    private final ModerationProperties properties;
    private final OrtEnvironment env;
    private final OrtSession session;

    /** true 면 NCHW {@code [1][3][H][W]}, false 면 NHWC {@code [1][H][W][3]} (동봉 모델) */
    private final boolean channelsFirst;

    public OnnxNsfwModerationService(ModerationProperties properties) {
        this.properties = properties;
        this.env = OrtEnvironment.getEnvironment();

        try {
            String modelPath = properties.getOnnx().getModelPath();
            byte[] modelBytes = loadModelBytes(modelPath);
            this.session = env.createSession(modelBytes, new OrtSession.SessionOptions());
            this.channelsFirst = detectChannelsFirst(this.session);
            log.info(
                    "OnnxNsfwModerationService 초기화 완료 - 모델: {}, 입력 레이아웃: {}, NSFW 임계값: {}",
                    modelPath,
                    getInputLayout(),
                    properties.getOnnx().getNsfwThreshold());
        } catch (OrtException | IOException e) {
            throw new IllegalStateException("ONNX NSFW 모델 로드 실패: " + e.getMessage(), e);
        }
    }

    // 테스트용 생성자 (동봉 모델과 같은 NHWC)
    OnnxNsfwModerationService(
            ModerationProperties properties, OrtEnvironment env, OrtSession session) {
        this(properties, env, session, false);
    }

    // 테스트용 생성자 — 레이아웃 지정
    OnnxNsfwModerationService(
            ModerationProperties properties,
            OrtEnvironment env,
            OrtSession session,
            boolean channelsFirst) {
        this.properties = properties;
        this.env = env;
        this.session = session;
        this.channelsFirst = channelsFirst;
    }

    /** 기동 로그·스모크 테스트용 — "NCHW" 또는 "NHWC" */
    public String getInputLayout() {
        return channelsFirst ? "NCHW" : "NHWC";
    }

    @Override
    public ImageModerationResult analyzeImage(MultipartFile imageFile) {
        log.debug(
                "ONNX NSFW 이미지 분석 시작: 파일={}, 크기={} bytes",
                imageFile.getOriginalFilename(),
                imageFile.getSize());

        try {
            BufferedImage image = ImageIO.read(imageFile.getInputStream());
            if (image == null) {
                log.warn("이미지 파일을 읽을 수 없습니다: {}", imageFile.getOriginalFilename());
                return ImageModerationResult.failOpen(
                        ImageModerationResult.REASON_UNREADABLE, getProviderName());
            }
            return runInference(image);
        } catch (IOException e) {
            log.error("이미지 파일 읽기 실패: {}", e.getMessage());
            return ImageModerationResult.failOpen(
                    ImageModerationResult.REASON_READ_ERROR, getProviderName());
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

    /** ONNX 모델 추론 실행 */
    ImageModerationResult runInference(BufferedImage image) {
        try {
            float[][][][] inputTensor = preprocessImage(image);

            try (OnnxTensor tensor = OnnxTensor.createTensor(env, inputTensor)) {
                OrtSession.Result result =
                        session.run(Map.of(session.getInputNames().iterator().next(), tensor));
                float[][] output = (float[][]) result.get(0).getValue();

                float nsfwScore = output[0][1]; // index 1 = NSFW score
                float sfwScore = output[0][0]; // index 0 = SFW score
                float nsfwThreshold = properties.getOnnx().getNsfwThreshold();

                log.debug(
                        "ONNX NSFW 분석 완료: SFW={}, NSFW={}, 임계값={}",
                        String.format("%.4f", sfwScore),
                        String.format("%.4f", nsfwScore),
                        nsfwThreshold);

                if (nsfwScore >= nsfwThreshold) {
                    double confidencePercent = nsfwScore * 100.0;
                    ModerationLabel label =
                            ModerationLabel.builder()
                                    .category(NSFW_CATEGORY)
                                    .name("NSFW Content")
                                    .confidence(confidencePercent)
                                    .build();

                    return ImageModerationResult.unsafe(
                            "부적절한 콘텐츠가 감지되었습니다 (NSFW: "
                                    + String.format("%.1f", confidencePercent)
                                    + "%)",
                            List.of(label),
                            Map.of(NSFW_CATEGORY, confidencePercent),
                            getProviderName());
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
            return ImageModerationResult.failOpen(
                    ImageModerationResult.REASON_INFERENCE_ERROR, getProviderName());
        }
    }

    /** 이미지 전처리: 224x224 리사이즈 + NCHW 정규화 OpenNSFW2 모델 기준: BGR 채널 순서, ImageNet mean 차감 */
    float[][][][] preprocessImage(BufferedImage original) {
        // 참조 구현과 동일: 256×256 bilinear 리사이즈 → 가운데 224×224 자르기
        BufferedImage resized =
                new BufferedImage(RESIZE_SIZE, RESIZE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, RESIZE_SIZE, RESIZE_SIZE, null);
        g.dispose();

        // 평균값 (BGR 순서) — Caffe open_nsfw 의 ImageNet mean
        float[] mean = {104.0f, 117.0f, 123.0f};

        float[][][][] tensor =
                channelsFirst
                        ? new float[1][3][IMAGE_SIZE][IMAGE_SIZE]
                        : new float[1][IMAGE_SIZE][IMAGE_SIZE][3];

        for (int y = 0; y < IMAGE_SIZE; y++) {
            for (int x = 0; x < IMAGE_SIZE; x++) {
                int rgb = resized.getRGB(x + CROP_OFFSET, y + CROP_OFFSET);
                float r = (float) ((rgb >> 16) & 0xFF);
                float g2 = (float) ((rgb >> 8) & 0xFF);
                float b = (float) (rgb & 0xFF);
                float[] bgr = {b - mean[0], g2 - mean[1], r - mean[2]};
                for (int c = 0; c < 3; c++) {
                    if (channelsFirst) {
                        tensor[0][c][y][x] = bgr[c];
                    } else {
                        tensor[0][y][x][c] = bgr[c];
                    }
                }
            }
        }

        return tensor;
    }

    /** 세션의 첫 입력 모양으로 레이아웃 판정. 정보를 못 읽으면 동봉 모델과 같은 NHWC 로 본다. */
    static boolean detectChannelsFirst(OrtSession session) {
        try {
            Map<String, NodeInfo> inputs = session.getInputInfo();
            if (inputs == null || inputs.isEmpty()) {
                return false;
            }
            NodeInfo first = inputs.values().iterator().next();
            if (first.getInfo() instanceof TensorInfo tensorInfo) {
                return isChannelsFirst(tensorInfo.getShape());
            }
            return false;
        } catch (Exception e) {
            log.warn("ONNX 입력 모양을 읽지 못해 NHWC 로 가정합니다: {}", e.getMessage());
            return false;
        }
    }

    /** {@code [N,3,H,W]} 면 NCHW. {@code [N,H,W,3]}·그 외는 NHWC */
    static boolean isChannelsFirst(long[] shape) {
        return shape != null && shape.length == 4 && shape[1] == 3 && shape[3] != 3;
    }

    private byte[] loadModelBytes(String modelPath) throws IOException {
        Resource resource = new DefaultResourceLoader().getResource(modelPath);
        try (InputStream is = resource.getInputStream()) {
            return is.readAllBytes();
        }
    }
}
