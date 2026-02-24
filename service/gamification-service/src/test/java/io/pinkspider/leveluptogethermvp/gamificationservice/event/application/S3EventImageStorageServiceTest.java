package io.pinkspider.leveluptogethermvp.gamificationservice.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.config.s3.S3ImageProperties;
import io.pinkspider.global.exception.CustomException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3EventImageStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private S3ImageProperties s3Properties;
    private EventImageProperties eventImageProperties;
    private S3EventImageStorageService s3EventImageStorageService;

    @BeforeEach
    void setUp() {
        s3Properties = new S3ImageProperties();
        s3Properties.setBucket("test-bucket");
        s3Properties.setCdnBaseUrl("https://cdn.example.com");

        eventImageProperties = new EventImageProperties();
        eventImageProperties.setMaxSize(10 * 1024 * 1024L); // 10MB
        eventImageProperties.setAllowedExtensions("jpg,jpeg,png,gif,webp");

        s3EventImageStorageService = new S3EventImageStorageService(
            s3Client, s3Properties, eventImageProperties
        );
    }

    @Nested
    @DisplayName("store 테스트")
    class StoreTest {

        @Test
        @DisplayName("유효한 이미지 파일을 S3에 저장하고 CDN URL을 반환한다")
        void store_success() throws IOException {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "test-image.jpg", "image/jpeg",
                "fake-image-content".getBytes()
            );
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            // when
            String result = s3EventImageStorageService.store(file);

            // then
            assertThat(result).isNotNull();
            assertThat(result).startsWith("https://cdn.example.com/events/");
            assertThat(result).endsWith(".jpg");
            verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("PNG 파일도 정상적으로 저장된다")
        void store_pngFile_success() throws IOException {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "test-image.png", "image/png",
                "fake-png-content".getBytes()
            );
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            // when
            String result = s3EventImageStorageService.store(file);

            // then
            assertThat(result).endsWith(".png");
        }

        @Test
        @DisplayName("파일이 null이면 예외를 발생시킨다")
        void store_nullFile_throwsException() {
            // when & then
            assertThatThrownBy(() -> s3EventImageStorageService.store(null))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("빈 파일이면 예외를 발생시킨다")
        void store_emptyFile_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "empty.jpg", "image/jpeg", new byte[0]
            );

            // when & then
            assertThatThrownBy(() -> s3EventImageStorageService.store(file))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("허용되지 않는 확장자 파일이면 예외를 발생시킨다")
        void store_invalidExtension_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "malicious.exe", "application/octet-stream",
                "fake-exe-content".getBytes()
            );

            // when & then
            assertThatThrownBy(() -> s3EventImageStorageService.store(file))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("파일 크기가 최대 크기를 초과하면 예외를 발생시킨다")
        void store_fileTooLarge_throwsException() {
            // given
            eventImageProperties.setMaxSize(10L); // 10 bytes로 매우 작게 설정
            byte[] largeContent = new byte[100];
            MockMultipartFile file = new MockMultipartFile(
                "image", "large-image.jpg", "image/jpeg", largeContent
            );

            // when & then
            assertThatThrownBy(() -> s3EventImageStorageService.store(file))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("image/가 아닌 MIME 타입이면 예외를 발생시킨다")
        void store_invalidMimeType_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "script.jpg", "text/javascript",
                "fake-content".getBytes()
            );

            // when & then
            assertThatThrownBy(() -> s3EventImageStorageService.store(file))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("delete 테스트")
    class DeleteTest {

        @Test
        @DisplayName("CDN URL에 해당하는 S3 객체를 삭제한다")
        void delete_withCdnUrl_success() {
            // given
            String imageUrl = "https://cdn.example.com/events/test-uuid.jpg";
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

            // when
            s3EventImageStorageService.delete(imageUrl);

            // then
            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("null URL이면 아무것도 하지 않는다")
        void delete_nullUrl_doesNothing() {
            // when
            s3EventImageStorageService.delete(null);

            // then - S3 클라이언트 호출 없음
        }

        @Test
        @DisplayName("빈 URL이면 아무것도 하지 않는다")
        void delete_emptyUrl_doesNothing() {
            // when
            s3EventImageStorageService.delete("");

            // then - S3 클라이언트 호출 없음
        }

        @Test
        @DisplayName("CDN URL이 아닌 외부 URL은 삭제하지 않는다")
        void delete_externalUrl_doesNothing() {
            // given
            String externalUrl = "https://external.cdn.com/some-image.jpg";

            // when
            s3EventImageStorageService.delete(externalUrl);

            // then - S3 클라이언트 호출 없음
        }

        @Test
        @DisplayName("S3 삭제 중 예외가 발생해도 무시된다")
        void delete_s3Exception_ignored() {
            // given
            String imageUrl = "https://cdn.example.com/events/test-uuid.jpg";
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 연결 실패"));

            // when & then - 예외가 발생하지 않아야 한다
            s3EventImageStorageService.delete(imageUrl);
        }
    }

    @Nested
    @DisplayName("isValidImage 테스트")
    class IsValidImageTest {

        @Test
        @DisplayName("유효한 JPEG 이미지 파일을 통과시킨다")
        void isValidImage_validJpeg_returnsTrue() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg",
                "fake-jpeg-content".getBytes()
            );

            // when
            boolean result = s3EventImageStorageService.isValidImage(file);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("유효한 PNG 이미지 파일을 통과시킨다")
        void isValidImage_validPng_returnsTrue() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "photo.png", "image/png",
                "fake-png-content".getBytes()
            );

            // when
            boolean result = s3EventImageStorageService.isValidImage(file);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("null 파일은 유효하지 않다")
        void isValidImage_null_returnsFalse() {
            // when
            boolean result = s3EventImageStorageService.isValidImage(null);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("빈 파일은 유효하지 않다")
        void isValidImage_emptyFile_returnsFalse() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "empty.jpg", "image/jpeg", new byte[0]
            );

            // when
            boolean result = s3EventImageStorageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("파일 크기 초과 시 유효하지 않다")
        void isValidImage_tooLarge_returnsFalse() {
            // given
            eventImageProperties.setMaxSize(10L);
            byte[] largeContent = new byte[100];
            MockMultipartFile file = new MockMultipartFile(
                "image", "large.jpg", "image/jpeg", largeContent
            );

            // when
            boolean result = s3EventImageStorageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("허용되지 않는 확장자는 유효하지 않다")
        void isValidImage_invalidExtension_returnsFalse() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "file.bmp", "image/bmp",
                "fake-content".getBytes()
            );

            // when
            boolean result = s3EventImageStorageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("image/ MIME 타입이 아니면 유효하지 않다")
        void isValidImage_nonImageMimeType_returnsFalse() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "script.jpg", "text/plain",
                "fake-content".getBytes()
            );

            // when
            boolean result = s3EventImageStorageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("파일 이름이 없으면 유효하지 않다")
        void isValidImage_noFilename_returnsFalse() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "", "image/jpeg",
                "fake-content".getBytes()
            );

            // when
            boolean result = s3EventImageStorageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("확장자가 없는 파일은 유효하지 않다")
        void isValidImage_noExtension_returnsFalse() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "noextension", "image/jpeg",
                "fake-content".getBytes()
            );

            // when
            boolean result = s3EventImageStorageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("WebP 파일은 유효하다")
        void isValidImage_webp_returnsTrue() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "photo.webp", "image/webp",
                "fake-webp-content".getBytes()
            );

            // when
            boolean result = s3EventImageStorageService.isValidImage(file);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("GIF 파일은 유효하다")
        void isValidImage_gif_returnsTrue() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "image", "animation.gif", "image/gif",
                "fake-gif-content".getBytes()
            );

            // when
            boolean result = s3EventImageStorageService.isValidImage(file);

            // then
            assertThat(result).isTrue();
        }
    }
}
