package io.pinkspider.leveluptogethermvp.userservice.mypage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.config.s3.S3ImageProperties;
import io.pinkspider.global.exception.CustomException;
import java.util.Arrays;
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3ProfileImageStorageServiceTest {

    @Mock private S3Client s3Client;

    @Mock private S3ImageProperties s3Properties;

    @Mock private ProfileImageProperties properties;

    private S3ProfileImageStorageService storageService;

    private static final String TEST_USER_ID = "test-user-456";
    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_CDN_BASE_URL = "https://cdn.example.com";

    @BeforeEach
    void setUp() {
        storageService = new S3ProfileImageStorageService(s3Client, s3Properties, properties);
    }

    @Nested
    @DisplayName("store 테스트")
    class StoreTest {

        @Test
        @DisplayName("null 파일이면 PROFILE_001 예외가 발생한다")
        void store_nullFile_throwsException() {
            // when & then
            assertThatThrownBy(() -> storageService.store(null, TEST_USER_ID))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("code", "PROFILE_001");
        }

        @Test
        @DisplayName("빈 파일이면 PROFILE_001 예외가 발생한다")
        void store_emptyFile_throwsException() {
            // given
            MockMultipartFile emptyFile =
                    new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);

            // when & then
            assertThatThrownBy(() -> storageService.store(emptyFile, TEST_USER_ID))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("code", "PROFILE_001");
        }

        @Test
        @DisplayName("유효하지 않은 이미지 파일이면 PROFILE_002 예외가 발생한다")
        void store_invalidImage_throwsException() {
            // given
            MockMultipartFile file =
                    new MockMultipartFile(
                            "file",
                            "test.exe",
                            "application/octet-stream",
                            "binary content".getBytes());
            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList())
                    .thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when & then
            assertThatThrownBy(() -> storageService.store(file, TEST_USER_ID))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("code", "PROFILE_002");
        }

        @Test
        @DisplayName("유효한 이미지 파일을 S3에 저장하고 CDN URL을 반환한다")
        void store_validImage_returnsS3CdnUrl() throws Exception {
            // given
            MockMultipartFile file =
                    new MockMultipartFile(
                            "file", "profile.jpg", "image/jpeg", "image content".getBytes());
            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList())
                    .thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));
            when(s3Properties.getBucket()).thenReturn(TEST_BUCKET);
            when(s3Properties.getCdnBaseUrl()).thenReturn(TEST_CDN_BASE_URL);
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            // when
            String result = storageService.store(file, TEST_USER_ID);

            // then
            assertThat(result).startsWith(TEST_CDN_BASE_URL + "/profile/" + TEST_USER_ID + "/");
            assertThat(result).endsWith(".jpg");
            verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("S3 업로드 실패(IOException) 시 PROFILE_003 예외가 발생한다")
        void store_s3IoFailure_throwsException() throws Exception {
            // given
            // getInputStream() 호출 시 IOException을 던지는 파일을 사용하여 catch(IOException) 블록을 트리거
            org.springframework.web.multipart.MultipartFile mockFile =
                    org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getSize()).thenReturn(100L);
            when(mockFile.getOriginalFilename()).thenReturn("profile.jpg");
            when(mockFile.getContentType()).thenReturn("image/jpeg");
            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList())
                    .thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));
            when(s3Properties.getBucket()).thenReturn(TEST_BUCKET);
            when(mockFile.getInputStream()).thenThrow(new java.io.IOException("스트림 읽기 오류"));

            // when & then
            assertThatThrownBy(() -> storageService.store(mockFile, TEST_USER_ID))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("code", "PROFILE_003");
        }
    }

    @Nested
    @DisplayName("delete 테스트")
    class DeleteTest {

        @Test
        @DisplayName("null URL이면 아무 작업도 하지 않는다")
        void delete_nullUrl_doesNothing() {
            // when
            storageService.delete(null);

            // then
            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("빈 URL이면 아무 작업도 하지 않는다")
        void delete_emptyUrl_doesNothing() {
            // when
            storageService.delete("");

            // then
            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("CDN URL이면 S3에서 파일을 삭제한다")
        void delete_cdnUrl_deletesFromS3() {
            // given
            when(s3Properties.getCdnBaseUrl()).thenReturn(TEST_CDN_BASE_URL);
            when(s3Properties.getBucket()).thenReturn(TEST_BUCKET);
            String imageUrl = TEST_CDN_BASE_URL + "/profile/user-1/image.jpg";

            // when
            storageService.delete(imageUrl);

            // then
            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("외부 URL(OAuth)이면 S3 삭제를 시도하지 않는다")
        void delete_externalUrl_doesNotDeleteFromS3() {
            // given
            when(s3Properties.getCdnBaseUrl()).thenReturn(TEST_CDN_BASE_URL);
            String externalUrl = "https://lh3.googleusercontent.com/profile/photo.jpg";

            // when
            storageService.delete(externalUrl);

            // then
            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("로컬 URL이면 S3 삭제를 시도하지 않는다")
        void delete_localUrl_doesNotDeleteFromS3() {
            // given
            when(s3Properties.getCdnBaseUrl()).thenReturn(TEST_CDN_BASE_URL);
            String localUrl = "/uploads/profile/user-1/image.jpg";

            // when
            storageService.delete(localUrl);

            // then
            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("S3 삭제 실패 시 예외를 전파하지 않는다")
        void delete_s3Failure_doesNotThrow() {
            // given
            when(s3Properties.getCdnBaseUrl()).thenReturn(TEST_CDN_BASE_URL);
            when(s3Properties.getBucket()).thenReturn(TEST_BUCKET);
            String imageUrl = TEST_CDN_BASE_URL + "/profile/user-1/image.jpg";
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(new RuntimeException("S3 삭제 오류"));

            // when (예외 없이 완료되어야 함)
            storageService.delete(imageUrl);
        }
    }

    @Nested
    @DisplayName("isValidImage 테스트")
    class IsValidImageTest {

        @Test
        @DisplayName("null 파일이면 false를 반환한다")
        void isValidImage_null_returnsFalse() {
            // when & then
            assertThat(storageService.isValidImage(null)).isFalse();
        }

        @Test
        @DisplayName("빈 파일이면 false를 반환한다")
        void isValidImage_empty_returnsFalse() {
            // given
            MockMultipartFile file =
                    new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);

            // when & then
            assertThat(storageService.isValidImage(file)).isFalse();
        }

        @Test
        @DisplayName("파일 크기가 초과하면 false를 반환한다")
        void isValidImage_sizeExceeded_returnsFalse() {
            // given
            byte[] largeContent = new byte[6000000]; // 6MB
            MockMultipartFile file =
                    new MockMultipartFile("file", "test.jpg", "image/jpeg", largeContent);
            when(properties.getMaxSize()).thenReturn(5242880L); // 5MB

            // when & then
            assertThat(storageService.isValidImage(file)).isFalse();
        }

        @Test
        @DisplayName("파일 이름이 null이면 false를 반환한다")
        void isValidImage_nullFilename_returnsFalse() {
            // given
            MockMultipartFile file =
                    new MockMultipartFile("file", null, "image/jpeg", "content".getBytes());
            when(properties.getMaxSize()).thenReturn(5242880L);

            // when & then
            assertThat(storageService.isValidImage(file)).isFalse();
        }

        @Test
        @DisplayName("허용되지 않은 확장자면 false를 반환한다")
        void isValidImage_disallowedExtension_returnsFalse() {
            // given
            MockMultipartFile file =
                    new MockMultipartFile("file", "test.exe", "image/jpeg", "content".getBytes());
            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList())
                    .thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when & then
            assertThat(storageService.isValidImage(file)).isFalse();
        }

        @Test
        @DisplayName("MIME 타입이 image로 시작하지 않으면 false를 반환한다")
        void isValidImage_nonImageMimeType_returnsFalse() {
            // given
            MockMultipartFile file =
                    new MockMultipartFile("file", "test.jpg", "text/plain", "content".getBytes());
            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList())
                    .thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when & then
            assertThat(storageService.isValidImage(file)).isFalse();
        }

        @Test
        @DisplayName("MIME 타입이 null이면 false를 반환한다")
        void isValidImage_nullMimeType_returnsFalse() {
            // given
            MockMultipartFile file =
                    new MockMultipartFile("file", "test.jpg", null, "content".getBytes());
            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList())
                    .thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when & then
            assertThat(storageService.isValidImage(file)).isFalse();
        }

        @Test
        @DisplayName("유효한 이미지 파일이면 true를 반환한다")
        void isValidImage_valid_returnsTrue() {
            // given
            MockMultipartFile file =
                    new MockMultipartFile(
                            "file", "test.png", "image/png", "image content".getBytes());
            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList())
                    .thenReturn(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

            // when & then
            assertThat(storageService.isValidImage(file)).isTrue();
        }
    }
}
