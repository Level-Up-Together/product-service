package io.pinkspider.leveluptogethermvp.userservice.moderation.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ImageModerationResult 테스트")
class ImageModerationResultTest {

    @Test
    @DisplayName("safe() 팩토리 메서드는 안전한 결과를 생성해야 함")
    void safe_shouldCreateSafeResult() {
        // when
        ImageModerationResult result = ImageModerationResult.safe();

        // then
        assertThat(result.isSafe()).isTrue();
        assertThat(result.getOverallConfidence()).isEqualTo(100.0);
        assertThat(result.getDetectedLabels()).isEmpty();
        assertThat(result.getCategoryScores()).isEmpty();
        assertThat(result.getProvider()).isEqualTo("none");
        assertThat(result.getRejectionReason()).isNull();
    }

    @Test
    @DisplayName("unsafe() 팩토리 메서드는 부적절 결과를 생성해야 함")
    void unsafe_shouldCreateUnsafeResult() {
        // given
        String reason = "부적절한 콘텐츠가 감지되었습니다";
        List<ModerationLabel> labels = List.of(
            ModerationLabel.builder()
                .category("Explicit Nudity")
                .name("Nudity")
                .confidence(95.5)
                .build()
        );
        Map<String, Double> scores = Map.of("Explicit Nudity", 95.5);
        String provider = "aws-rekognition";

        // when
        ImageModerationResult result = ImageModerationResult.unsafe(reason, labels, scores, provider);

        // then
        assertThat(result.isSafe()).isFalse();
        assertThat(result.getRejectionReason()).isEqualTo(reason);
        assertThat(result.getDetectedLabels()).hasSize(1);
        assertThat(result.getDetectedLabels().get(0).getCategory()).isEqualTo("Explicit Nudity");
        assertThat(result.getCategoryScores()).containsEntry("Explicit Nudity", 95.5);
        assertThat(result.getProvider()).isEqualTo(provider);
    }

    @Test
    @DisplayName("빌더로 결과를 생성할 수 있어야 함")
    void builder_shouldCreateResult() {
        // given
        ModerationLabel label = ModerationLabel.builder()
            .category("Violence")
            .name("Graphic Violence")
            .confidence(80.0)
            .parentName("Violence")
            .build();

        // when
        ImageModerationResult result = ImageModerationResult.builder()
            .safe(false)
            .overallConfidence(80.0)
            .detectedLabels(List.of(label))
            .categoryScores(Map.of("Violence", 80.0))
            .rejectionReason("폭력적인 콘텐츠가 감지되었습니다")
            .provider("aws-rekognition")
            .build();

        // then
        assertThat(result.isSafe()).isFalse();
        assertThat(result.getOverallConfidence()).isEqualTo(80.0);
        assertThat(result.getDetectedLabels()).hasSize(1);
        assertThat(result.getCategoryScores()).containsKey("Violence");
    }
}
