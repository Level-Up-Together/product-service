package io.pinkspider.global.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

@DisplayName("ImageModerationMetrics 테스트 (LUT-650)")
class ImageModerationMetricsTest {

    @SuppressWarnings("unchecked")
    private static ImageModerationMetrics withRegistry(MeterRegistry registry) {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return new ImageModerationMetrics(provider);
    }

    @Test
    @DisplayName("checked 는 provider·result 라벨로, failOpen 은 provider·reason 라벨로 센다")
    void countsWithTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ImageModerationMetrics metrics = withRegistry(registry);

        metrics.checked("onnx-nsfw", ImageModerationMetrics.RESULT_SAFE);
        metrics.checked("onnx-nsfw", ImageModerationMetrics.RESULT_SAFE);
        metrics.checked("onnx-nsfw", ImageModerationMetrics.RESULT_BLOCKED);
        metrics.failOpen("onnx-nsfw", "read_error");

        assertThat(
                        registry.get(ImageModerationMetrics.CHECKED)
                                .tag("provider", "onnx-nsfw")
                                .tag("result", "safe")
                                .counter()
                                .count())
                .isEqualTo(2.0);
        assertThat(
                        registry.get(ImageModerationMetrics.CHECKED)
                                .tag("result", "blocked")
                                .counter()
                                .count())
                .isEqualTo(1.0);
        assertThat(
                        registry.get(ImageModerationMetrics.FAIL_OPEN)
                                .tag("provider", "onnx-nsfw")
                                .tag("reason", "read_error")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("라벨 값이 null 이면 unknown 으로 센다 (라벨 누락으로 등록 실패하지 않게)")
    void nullTagsBecomeUnknown() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ImageModerationMetrics metrics = withRegistry(registry);

        metrics.failOpen(null, null);

        assertThat(
                        registry.get(ImageModerationMetrics.FAIL_OPEN)
                                .tag("provider", "unknown")
                                .tag("reason", "unknown")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("MeterRegistry 가 없으면 아무것도 하지 않는다 (슬라이스 테스트)")
    void noopWithoutRegistry() {
        ImageModerationMetrics metrics = withRegistry(null);

        assertThatCode(
                        () -> {
                            metrics.checked("onnx-nsfw", ImageModerationMetrics.RESULT_SAFE);
                            metrics.failOpen("onnx-nsfw", "exception");
                        })
                .doesNotThrowAnyException();
    }
}
