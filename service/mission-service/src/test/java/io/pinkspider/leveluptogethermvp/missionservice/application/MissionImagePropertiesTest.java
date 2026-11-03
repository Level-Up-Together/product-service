package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MissionImageProperties 기본값 (LUT-608)")
class MissionImagePropertiesTest {

    @Test
    @DisplayName("미션 기록 사진 1장 기본 상한은 10MB — 웹 mission-record 검사값·multipart max-file-size 와 같다")
    void defaultMaxSizeIsTenMegabytes() {
        assertThat(new MissionImageProperties().getMaxSize()).isEqualTo(10L * 1024 * 1024);
    }

    @Test
    @DisplayName("허용 확장자 목록은 소문자 콤마 분리")
    void allowedExtensions() {
        assertThat(new MissionImageProperties().getAllowedExtensionList())
                .containsExactly("jpg", "jpeg", "png", "gif", "webp");
    }
}
