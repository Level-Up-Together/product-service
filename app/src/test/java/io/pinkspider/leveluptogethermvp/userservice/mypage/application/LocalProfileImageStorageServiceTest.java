package io.pinkspider.leveluptogethermvp.userservice.mypage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class LocalProfileImageStorageServiceTest {

    @Mock
    private ProfileImageProperties properties;

    private LocalProfileImageStorageService storageService;

    @TempDir
    Path tempDir;

    private static final String TEST_USER_ID = "test-user-123";
    private static final String URL_PREFIX = "/uploads/profile";

    @BeforeEach
    void setUp() {
        storageService = new LocalProfileImageStorageService(properties);
    }

    @Nested
    @DisplayName("store 테스트")
    class StoreTest {

        @Test
        @DisplayName("null 파일이면 예외가 발생한다")
        void store_nullFile_throwsException() {
            // when & then
            assertThatThrownBy(() -> storageService.store(null, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "PROFILE_001");
        }

        @Test
        @DisplayName("빈 파일이면 예외가 발생한다")
        void store_emptyFile_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[0]
            );

            // when & then
            assertThatThrownBy(() -> storageService.store(file, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "PROFILE_001");
        }

        @Test
        @DisplayName("유효하지 않은 이미지 파일이면 예외가 발생한다")
        void store_invalidImage_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList()).thenReturn(java.util.Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when & then
            assertThatThrownBy(() -> storageService.store(file, TEST_USER_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "PROFILE_002");
        }

        @Test
        @DisplayName("이미지 파일을 저장한다")
        void store_success() throws IOException {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "test image content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList()).thenReturn(java.util.Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));
            when(properties.getPath()).thenReturn(tempDir.toString());
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);

            // when
            String result = storageService.store(file, TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result).startsWith(URL_PREFIX + "/" + TEST_USER_ID + "/");
            assertThat(result).endsWith(".jpg");
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
            storageService.delete("https://external.com/image.jpg");

            // then: 예외 없이 성공 (외부 URL 무시)
        }

        @Test
        @DisplayName("로컬 파일을 삭제한다")
        void delete_success() throws IOException {
            // given
            Path userDir = tempDir.resolve(TEST_USER_ID);
            Files.createDirectories(userDir);
            Path testFile = userDir.resolve("test-image.jpg");
            Files.write(testFile, "test content".getBytes());

            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);
            when(properties.getPath()).thenReturn(tempDir.toString());

            String imageUrl = URL_PREFIX + "/" + TEST_USER_ID + "/test-image.jpg";

            // when
            storageService.delete(imageUrl);

            // then
            assertThat(Files.exists(testFile)).isFalse();
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
            byte[] largeContent = new byte[6000000]; // 6MB
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", largeContent
            );

            when(properties.getMaxSize()).thenReturn(5242880L); // 5MB

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
                "file", "test.txt", "image/jpeg", "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList()).thenReturn(java.util.Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

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

            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList()).thenReturn(java.util.Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("유효한 이미지면 true를 반환한다")
        void isValidImage_valid_returnsTrue() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "test content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList()).thenReturn(java.util.Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

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

            when(properties.getMaxSize()).thenReturn(5242880L);

            // when
            boolean result = storageService.isValidImage(file);

            // then
            assertThat(result).isFalse();
        }
    }
}
