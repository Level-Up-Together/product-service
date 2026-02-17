package io.pinkspider.leveluptogethermvp.userservice.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.moderation.application.AwsRekognitionModerationService;
import io.pinkspider.global.moderation.config.ModerationProperties;
import io.pinkspider.global.moderation.domain.dto.ImageModerationResult;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class AwsRekognitionModerationServiceTest {

    private AwsRekognitionModerationService service;
    private ModerationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ModerationProperties();
        properties.setProvider("aws-rekognition");
        properties.setMinConfidence(80.0);
        ModerationProperties.AwsConfig awsConfig = new ModerationProperties.AwsConfig();
        awsConfig.setRegion("ap-northeast-2");
        properties.setAws(awsConfig);

        service = new AwsRekognitionModerationService(properties);
    }

    @Nested
    @DisplayName("analyzeImage 테스트")
    class AnalyzeImageTest {

        @Test
        @DisplayName("이미지 파일을 분석하면 안전한 결과를 반환한다")
        void analyzeImage_success() throws IOException {
            // given
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getOriginalFilename()).thenReturn("test.jpg");
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getBytes()).thenReturn(new byte[1024]);

            // when
            ImageModerationResult result = service.analyzeImage(mockFile);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isSafe()).isTrue();
        }

        @Test
        @DisplayName("파일 읽기 실패 시 안전한 결과를 반환한다")
        void analyzeImage_ioException_returnsSafe() throws IOException {
            // given
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.getOriginalFilename()).thenReturn("test.jpg");
            when(mockFile.getSize()).thenReturn(1024L);
            when(mockFile.getBytes()).thenThrow(new IOException("파일 읽기 실패"));

            // when
            ImageModerationResult result = service.analyzeImage(mockFile);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isSafe()).isTrue();
        }
    }

    @Nested
    @DisplayName("analyzeImageUrl 테스트")
    class AnalyzeImageUrlTest {

        @Test
        @DisplayName("URL 이미지 분석은 안전한 결과를 반환한다")
        void analyzeImageUrl_success() {
            // given
            String imageUrl = "https://example.com/image.jpg";

            // when
            ImageModerationResult result = service.analyzeImageUrl(imageUrl);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isSafe()).isTrue();
        }
    }

    @Nested
    @DisplayName("isEnabled 테스트")
    class IsEnabledTest {

        @Test
        @DisplayName("활성화 상태를 반환한다")
        void isEnabled_returnsTrue() {
            // when
            boolean result = service.isEnabled();

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("getProviderName 테스트")
    class GetProviderNameTest {

        @Test
        @DisplayName("프로바이더 이름을 반환한다")
        void getProviderName_returnsAwsRekognition() {
            // when
            String result = service.getProviderName();

            // then
            assertThat(result).isEqualTo("aws-rekognition");
        }
    }
}
