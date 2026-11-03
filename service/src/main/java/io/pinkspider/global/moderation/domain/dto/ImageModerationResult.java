package io.pinkspider.global.moderation.domain.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이미지 검증 결과 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageModerationResult {

    /** 이미지가 안전한지 여부 true: 안전함 (업로드 허용) false: 부적절한 콘텐츠 감지됨 (업로드 차단) */
    private boolean safe;

    /** 전체 분석 신뢰도 (0.0 ~ 100.0) */
    private double overallConfidence;

    /** 감지된 레이블 목록 */
    private List<ModerationLabel> detectedLabels;

    /** 카테고리별 점수 (카테고리명 -> 점수) 예: {"Explicit Nudity": 95.5, "Violence": 12.3} */
    private Map<String, Double> categoryScores;

    /** 차단 사유 메시지 (safe=false인 경우) */
    private String rejectionReason;

    /** 분석 제공자 (none, onnx-nsfw, aws-rekognition 등) */
    private String provider;

    /**
     * LUT-650: 검수 자체가 실패해 판정을 못 한 결과. {@code safe=true} 로 두되 이 표시를 남겨, 통과/차단 정책은 {@code
     * ImageModerationAspect} 가 {@code moderation.image.fail-open} 설정으로 한곳에서 적용한다.
     */
    private boolean failOpen;

    /** failOpen 사유 — 카운터 라벨이라 고정 집합({@link #REASON_UNREADABLE} 등)만 쓴다 */
    private String failureReason;

    /** 파일을 이미지로 해석할 수 없음 (ImageIO 가 null — WebP 등 기본 Java 미지원 포맷 포함) */
    public static final String REASON_UNREADABLE = "unreadable";

    /** 파일 읽기 I/O 실패 */
    public static final String REASON_READ_ERROR = "read_error";

    /** 추론·외부 API 호출 실패 */
    public static final String REASON_INFERENCE_ERROR = "inference_error";

    /** 제공자가 결과 대신 런타임 예외(또는 null)를 돌려줌 — Aspect 가 감싼다 */
    public static final String REASON_EXCEPTION = "exception";

    /** 안전한 결과 생성 (NoOp용) */
    public static ImageModerationResult safe() {
        return ImageModerationResult.builder()
                .safe(true)
                .overallConfidence(100.0)
                .detectedLabels(List.of())
                .categoryScores(Map.of())
                .provider("none")
                .build();
    }

    /** LUT-650: 검수 실패 결과 — safe 이지만 failOpen 표시. 정책 적용은 Aspect 몫 */
    public static ImageModerationResult failOpen(String reason, String provider) {
        return ImageModerationResult.builder()
                .safe(true)
                .failOpen(true)
                .failureReason(reason)
                .overallConfidence(0.0)
                .detectedLabels(List.of())
                .categoryScores(Map.of())
                .provider(provider)
                .build();
    }

    /** 부적절 결과 생성 */
    public static ImageModerationResult unsafe(
            String reason,
            List<ModerationLabel> labels,
            Map<String, Double> scores,
            String provider) {
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
