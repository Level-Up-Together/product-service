package io.pinkspider.leveluptogethermvp.userservice.moderation.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.moderation.config.ModerationProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ModerationProperties 테스트")
class ModerationPropertiesTest {

    @Test
    @DisplayName("기본값은 none provider, 비활성화 상태여야 함")
    void defaultValues_shouldBeNoneAndDisabled() {
        // given
        ModerationProperties properties = new ModerationProperties();

        // when & then
        assertThat(properties.getProvider()).isEqualTo("none");
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getMinConfidence()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("provider가 none이면 비활성화 상태")
    void isEnabled_shouldBeFalseWhenProviderIsNone() {
        // given
        ModerationProperties properties = new ModerationProperties();
        properties.setProvider("none");

        // when & then
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("provider가 null이면 비활성화 상태")
    void isEnabled_shouldBeFalseWhenProviderIsNull() {
        // given
        ModerationProperties properties = new ModerationProperties();
        properties.setProvider(null);

        // when & then
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("provider가 aws-rekognition이면 활성화 상태")
    void isEnabled_shouldBeTrueWhenProviderIsAwsRekognition() {
        // given
        ModerationProperties properties = new ModerationProperties();
        properties.setProvider("aws-rekognition");

        // when & then
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("provider가 onnx-nsfw이면 활성화 상태")
    void isEnabled_shouldBeTrueWhenProviderIsOnnxNsfw() {
        // given
        ModerationProperties properties = new ModerationProperties();
        properties.setProvider("onnx-nsfw");

        // when & then
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("차단 카테고리 기본값이 설정되어 있어야 함")
    void blockedCategories_shouldHaveDefaultValues() {
        // given
        ModerationProperties properties = new ModerationProperties();

        // when
        List<String> blockedCategories = properties.getBlockedCategories();

        // then
        assertThat(blockedCategories).isNotEmpty();
        assertThat(blockedCategories).contains("Explicit Nudity", "Violence");
    }

    @Test
    @DisplayName("AWS 설정 기본값이 설정되어 있어야 함")
    void awsConfig_shouldHaveDefaultValues() {
        // given
        ModerationProperties properties = new ModerationProperties();

        // when
        ModerationProperties.AwsConfig awsConfig = properties.getAws();

        // then
        assertThat(awsConfig).isNotNull();
        assertThat(awsConfig.getRegion()).isEqualTo("ap-northeast-2");
    }

    @Test
    @DisplayName("ONNX 설정 기본값이 설정되어 있어야 함")
    void onnxConfig_shouldHaveDefaultValues() {
        // given
        ModerationProperties properties = new ModerationProperties();

        // when
        ModerationProperties.OnnxConfig onnxConfig = properties.getOnnx();

        // then
        assertThat(onnxConfig).isNotNull();
        assertThat(onnxConfig.getModelPath()).isEqualTo("classpath:models/nsfw.onnx");
        assertThat(onnxConfig.getNsfwThreshold()).isEqualTo(0.8f);
    }

    @Test
    @DisplayName("setter로 값을 변경할 수 있어야 함")
    void setters_shouldUpdateValues() {
        // given
        ModerationProperties properties = new ModerationProperties();

        // when
        properties.setProvider("aws-rekognition");
        properties.setMinConfidence(90.0);
        properties.setBlockedCategories(List.of("Custom Category"));

        // then
        assertThat(properties.getProvider()).isEqualTo("aws-rekognition");
        assertThat(properties.getMinConfidence()).isEqualTo(90.0);
        assertThat(properties.getBlockedCategories()).containsExactly("Custom Category");
        assertThat(properties.isEnabled()).isTrue();
    }
}
