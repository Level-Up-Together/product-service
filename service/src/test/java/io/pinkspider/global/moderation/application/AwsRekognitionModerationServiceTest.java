package io.pinkspider.global.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.moderation.config.ModerationProperties;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("AwsRekognitionModerationService 테스트")
class AwsRekognitionModerationServiceTest {

    private static AwsRekognitionModerationService service() {
        ModerationProperties properties = new ModerationProperties();
        properties.setProvider("aws-rekognition");
        return new AwsRekognitionModerationService(properties);
    }

    @Test
    @DisplayName("활성 제공자이며 이름은 aws-rekognition 이다")
    void enabledAndName() {
        AwsRekognitionModerationService service = service();

        assertThat(service.isEnabled()).isTrue();
        assertThat(service.getProviderName()).isEqualTo("aws-rekognition");
    }

    @Test
    @DisplayName("파일 읽기 IOException 은 fail-open 결과(read_error)로 돌려준다 — 조용한 통과가 아니다 (LUT-650)")
    void readErrorIsFailOpen() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("a.jpg");
        when(file.getSize()).thenReturn(10L);
        when(file.getBytes()).thenThrow(new IOException("boom"));

        ImageModerationResult result = service().analyzeImage(file);

        assertThat(result.isSafe()).isTrue();
        assertThat(result.isFailOpen()).isTrue();
        assertThat(result.getFailureReason()).isEqualTo(ImageModerationResult.REASON_READ_ERROR);
        assertThat(result.getProvider()).isEqualTo("aws-rekognition");
    }

    @Test
    @DisplayName("스켈레톤 모드에서 정상 파일은 안전(판정 완료)으로 돌려준다")
    void skeletonReturnsSafeNotFailOpen() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("a.jpg");
        when(file.getSize()).thenReturn(3L);
        when(file.getBytes()).thenReturn(new byte[] {1, 2, 3});

        ImageModerationResult result = service().analyzeImage(file);

        assertThat(result.isSafe()).isTrue();
        assertThat(result.isFailOpen()).isFalse();
    }
}
