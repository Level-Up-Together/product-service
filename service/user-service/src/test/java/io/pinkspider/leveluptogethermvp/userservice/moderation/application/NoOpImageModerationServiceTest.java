package io.pinkspider.leveluptogethermvp.userservice.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.moderation.application.NoOpImageModerationService;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("NoOpImageModerationService 테스트")
class NoOpImageModerationServiceTest {

    private NoOpImageModerationService noOpImageModerationService;

    @BeforeEach
    void setUp() {
        noOpImageModerationService = new NoOpImageModerationService();
    }

    @Test
    @DisplayName("isEnabled()는 항상 false를 반환해야 함")
    void isEnabled_shouldReturnFalse() {
        // when
        boolean result = noOpImageModerationService.isEnabled();

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getProviderName()은 'none'을 반환해야 함")
    void getProviderName_shouldReturnNone() {
        // when
        String result = noOpImageModerationService.getProviderName();

        // then
        assertThat(result).isEqualTo("none");
    }

    @Test
    @DisplayName("analyzeImage()는 항상 안전한 결과를 반환해야 함")
    void analyzeImage_shouldReturnSafeResult() {
        // given
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("test.jpg");
        when(mockFile.getSize()).thenReturn(1024L);

        // when
        ImageModerationResult result = noOpImageModerationService.analyzeImage(mockFile);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSafe()).isTrue();
        assertThat(result.getDetectedLabels()).isEmpty();
        assertThat(result.getProvider()).isEqualTo("none");
    }

    @Test
    @DisplayName("analyzeImageUrl()은 항상 안전한 결과를 반환해야 함")
    void analyzeImageUrl_shouldReturnSafeResult() {
        // given
        String imageUrl = "https://example.com/test.jpg";

        // when
        ImageModerationResult result = noOpImageModerationService.analyzeImageUrl(imageUrl);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSafe()).isTrue();
        assertThat(result.getDetectedLabels()).isEmpty();
        assertThat(result.getProvider()).isEqualTo("none");
    }
}
