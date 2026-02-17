package io.pinkspider.global.moderation.domain.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이미지 검증 결과
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageModerationResult {

    /**
     * 이미지가 안전한지 여부
     * true: 안전함 (업로드 허용)
     * false: 부적절한 콘텐츠 감지됨 (업로드 차단)
     */
    private boolean safe;

    /**
     * 전체 분석 신뢰도 (0.0 ~ 100.0)
     */
    private double overallConfidence;

    /**
     * 감지된 레이블 목록
     */
    private List<ModerationLabel> detectedLabels;

    /**
     * 카테고리별 점수 (카테고리명 -> 점수)
     * 예: {"Explicit Nudity": 95.5, "Violence": 12.3}
     */
    private Map<String, Double> categoryScores;

    /**
     * 차단 사유 메시지 (safe=false인 경우)
     */
    private String rejectionReason;

    /**
     * 분석 제공자 (none, onnx-nsfw, aws-rekognition 등)
     */
    private String provider;

    /**
     * 안전한 결과 생성 (NoOp용)
     */
    public static ImageModerationResult safe() {
        return ImageModerationResult.builder()
            .safe(true)
            .overallConfidence(100.0)
            .detectedLabels(List.of())
            .categoryScores(Map.of())
            .provider("none")
            .build();
    }

    /**
     * 부적절 결과 생성
     */
    public static ImageModerationResult unsafe(String reason, List<ModerationLabel> labels,
                                                Map<String, Double> scores, String provider) {
        return ImageModerationResult.builder()
            .safe(false)
            .overallConfidence(labels.isEmpty() ? 0.0 : labels.get(0).getConfidence())
            .detectedLabels(labels)
            .categoryScores(scores)
            .rejectionReason(reason)
            .provider(provider)
            .build();
    }
}
