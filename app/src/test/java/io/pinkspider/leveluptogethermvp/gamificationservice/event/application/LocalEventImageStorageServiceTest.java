package io.pinkspider.leveluptogethermvp.gamificationservice.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class LocalEventImageStorageServiceTest {

    @Mock
    private EventImageProperties properties;

    private LocalEventImageStorageService storageService;

    @TempDir
    Path tempDir;

    private static final String URL_PREFIX = "/uploads/events";

    @BeforeEach
    void setUp() {
        storageService = new LocalEventImageStorageService(properties);
    }

    @Nested
    @DisplayName("store 테스트")
    class StoreTest {

        @Test
        @DisplayName("null 파일이면 예외가 발생한다")
        void store_nullFile_throwsException() {
            // when & then
            assertThatThrownBy(() -> storageService.store(null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "EVENT_IMAGE_001");
        }

        @Test
        @DisplayName("빈 파일이면 예외가 발생한다")
        void store_emptyFile_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[0]
            );

            // when & then
            assertThatThrownBy(() -> storageService.store(file))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "EVENT_IMAGE_001");
        }

        @Test
        @DisplayName("유효하지 않은 이미지 파일이면 예외가 발생한다")
        void store_invalidImage_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L); // 10MB
            when(properties.getAllowedExtensionList()).thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when & then
            assertThatThrownBy(() -> storageService.store(file))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "EVENT_IMAGE_002");
        }

        @Test
        @DisplayName("이미지 파일을 저장한다")
        void store_success() throws IOException {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "event-banner.jpg", "image/jpeg", "test image content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L);
            when(properties.getAllowedExtensionList()).thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));
            when(properties.getPath()).thenReturn(tempDir.toString());
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);

            // when
            String result = storageService.store(file);

            // then
            assertThat(result).isNotNull();
            assertThat(result).startsWith(URL_PREFIX + "/");
            assertThat(result).endsWith(".jpg");
        }

        @Test
        @DisplayName("PNG 이미지를 저장한다")
        void store_pngImage_success() throws IOException {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "event-banner.png", "image/png", "png image content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L);
            when(properties.getAllowedExtensionList()).thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));
            when(properties.getPath()).thenReturn(tempDir.toString());
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);

            // when
            String result = storageService.store(file);

            // then
            assertThat(result).isNotNull();
            assertThat(result).endsWith(".png");
        }

        @Test
        @DisplayName("WebP 이미지를 저장한다")
        void store_webpImage_success() throws IOException {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "event-banner.webp", "image/webp", "webp image content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L);
            when(properties.getAllowedExtensionList()).thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));
            when(properties.getPath()).thenReturn(tempDir.toString());
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);

            // when
            String result = storageService.store(file);

            // then
            assertThat(result).isNotNull();
            assertThat(result).endsWith(".webp");
        }
    }

    @Nested
    @DisplayName("delete 테스트")
    class DeleteTest {

        @Test
        @DisplayName("null URL이면 아무 작업도 하지 않는다")
        void delete_nullUrl() {
            // when
            storageService.delete(null);

            // then: 예외 없이 성공
        }

        @Test
        @DisplayName("빈 URL이면 아무 작업도 하지 않는다")
        void delete_emptyUrl() {
            // when
            storageService.delete("");

            // then: 예외 없이 성공
        }

        @Test
        @DisplayName("외부 URL이면 삭제하지 않는다")
        void delete_externalUrl() {
            // given
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);

            // when
            storageService.delete("https://cdn.example.com/image.jpg");

            // then: 예외 없이 성공 (외부 URL 무시)
        }

        @Test
        @DisplayName("로컬 파일을 삭제한다")
        void delete_success() throws IOException {
            // given
            Path testFile = tempDir.resolve("test-image.jpg");
            Files.write(testFile, "test content".getBytes());

            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);
            when(properties.getPath()).thenReturn(tempDir.toString());

            String imageUrl = URL_PREFIX + "/test-image.jpg";

            // when
            storageService.delete(imageUrl);

            // then
            assertThat(Files.exists(testFile)).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 파일 삭제 시도는 무시된다")
        void delete_nonExistentFile() {
            // given
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);
            when(properties.getPath()).thenReturn(tempDir.toString());

            String imageUrl = URL_PREFIX + "/non-existent.jpg";

            // when
            storageService.delete(imageUrl);

            // then: 예외 없이 성공
        }
    }

    @Nested
    @DisplayName("isValidImage 테스트")
    class IsValidImageTest {

        @Test
        @DisplayName("null 파일이면 false를 반환한다")
        void isValidImage_null_returnsFalse() {
            // when
            boolean result = storageService.isValidImage(null);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("빈 파일이면 false를 반환한다")
        void isValidImage_empty_returnsFalse() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[0]
            );

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("파일 크기가 초과하면 false를 반환한다")
        void isValidImage_sizeExceeded_returnsFalse() {
            // given
            byte[] largeContent = new byte[11000000]; // 11MB
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", largeContent
            );

            when(properties.getMaxSize()).thenReturn(10485760L); // 10MB

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("허용되지 않은 확장자면 false를 반환한다")
        void isValidImage_invalidExtension_returnsFalse() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.bmp", "image/bmp", "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L);
            when(properties.getAllowedExtensionList()).thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("MIME 타입이 이미지가 아니면 false를 반환한다")
        void isValidImage_invalidMimeType_returnsFalse() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "text/plain", "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L);
            when(properties.getAllowedExtensionList()).thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("유효한 JPEG 이미지면 true를 반환한다")
        void isValidImage_validJpeg_returnsTrue() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L);
            when(properties.getAllowedExtensionList()).thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("유효한 PNG 이미지면 true를 반환한다")
        void isValidImage_validPng_returnsTrue() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L);
            when(properties.getAllowedExtensionList()).thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("유효한 GIF 이미지면 true를 반환한다")
        void isValidImage_validGif_returnsTrue() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.gif", "image/gif", "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L);
            when(properties.getAllowedExtensionList()).thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("유효한 WebP 이미지면 true를 반환한다")
        void isValidImage_validWebp_returnsTrue() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.webp", "image/webp", "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L);
            when(properties.getAllowedExtensionList()).thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("파일 이름이 null이면 false를 반환한다")
        void isValidImage_nullFilename_returnsFalse() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", null, "image/jpeg", "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L);

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("파일 이름이 빈 문자열이면 false를 반환한다")
        void isValidImage_emptyFilename_returnsFalse() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "", "image/jpeg", "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L);

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("MIME 타입이 null이면 false를 반환한다")
        void isValidImage_nullMimeType_returnsFalse() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", null, "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(10485760L);
            when(properties.getAllowedExtensionList()).thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }
    }
}
