package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import io.pinkspider.global.component.ImageResizer;
import io.pinkspider.global.exception.CustomException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
class LocalMissionImageStorageServiceTest {

    @Mock
    private MissionImageProperties properties;

    @Mock
    private ImageResizer imageResizer;

    private LocalMissionImageStorageService storageService;

    @TempDir
    Path tempDir;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long MISSION_ID = 1L;
    private static final String EXECUTION_DATE = "2024-01-15";
    private static final String URL_PREFIX = "/uploads/missions";

    @BeforeEach
    void setUp() {
        storageService = new LocalMissionImageStorageService(properties, imageResizer);
    }

    @Nested
    @DisplayName("store 테스트")
    class StoreTest {

        @Test
        @DisplayName("null 파일이면 예외가 발생한다")
        void store_nullFile_throwsException() {
            // when & then
            assertThatThrownBy(() -> storageService.store(null, TEST_USER_ID, MISSION_ID, EXECUTION_DATE))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "MISSION_IMAGE_001");
        }

        @Test
        @DisplayName("빈 파일이면 예외가 발생한다")
        void store_emptyFile_throwsException() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[0]
            );

            // when & then
            assertThatThrownBy(() -> storageService.store(file, TEST_USER_ID, MISSION_ID, EXECUTION_DATE))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "MISSION_IMAGE_001");
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
            assertThatThrownBy(() -> storageService.store(file, TEST_USER_ID, MISSION_ID, EXECUTION_DATE))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "MISSION_IMAGE_002");
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
            String result = storageService.store(file, TEST_USER_ID, MISSION_ID, EXECUTION_DATE);

            // then
            assertThat(result).isNotNull();
            assertThat(result).startsWith(URL_PREFIX + "/" + TEST_USER_ID + "/" + MISSION_ID + "/" + EXECUTION_DATE);
            assertThat(result).endsWith(".jpg");
        }

        @Test
        @DisplayName("LUT-400: 리사이즈 변형이 생성되면 thumb/medium 파일도 함께 저장한다")
        void store_withVariants_savesThumbAndMediumFiles() throws IOException {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "test image content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList()).thenReturn(java.util.Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));
            when(properties.getPath()).thenReturn(tempDir.toString());
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);
            when(imageResizer.resize(any(byte[].class), org.mockito.ArgumentMatchers.eq("jpg"), anyInt()))
                .thenReturn(Optional.of("thumb-bytes".getBytes()));

            // when
            String result = storageService.store(file, TEST_USER_ID, MISSION_ID, EXECUTION_DATE);

            // then
            String originalFilename = result.substring(result.lastIndexOf('/') + 1);
            String baseName = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
            Path userDir = tempDir.resolve(TEST_USER_ID).resolve(String.valueOf(MISSION_ID));
            assertThat(Files.exists(userDir.resolve(baseName + "_thumb.jpg"))).isTrue();
            assertThat(Files.exists(userDir.resolve(baseName + "_medium.jpg"))).isTrue();
        }

        @Test
        @DisplayName("LUT-400: 변형 생성이 비어있으면 원본만 저장한다")
        void store_variantEmpty_savesOriginalOnly() throws IOException {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "test image content".getBytes()
            );

            when(properties.getMaxSize()).thenReturn(5242880L);
            when(properties.getAllowedExtensionList()).thenReturn(java.util.Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));
            when(properties.getPath()).thenReturn(tempDir.toString());
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);
            when(imageResizer.resize(any(byte[].class), org.mockito.ArgumentMatchers.anyString(), anyInt()))
                .thenReturn(Optional.empty());

            // when
            String result = storageService.store(file, TEST_USER_ID, MISSION_ID, EXECUTION_DATE);

            // then
            String originalFilename = result.substring(result.lastIndexOf('/') + 1);
            String baseName = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
            Path userDir = tempDir.resolve(TEST_USER_ID).resolve(String.valueOf(MISSION_ID));
            assertThat(Files.exists(userDir.resolve(originalFilename))).isTrue();
            assertThat(Files.exists(userDir.resolve(baseName + "_thumb.jpg"))).isFalse();
            assertThat(Files.exists(userDir.resolve(baseName + "_medium.jpg"))).isFalse();
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
            Path userDir = tempDir.resolve(TEST_USER_ID).resolve(String.valueOf(MISSION_ID));
            Files.createDirectories(userDir);
            Path testFile = userDir.resolve("test-image.jpg");
            Files.write(testFile, "test content".getBytes());

            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);
            when(properties.getPath()).thenReturn(tempDir.toString());

            String imageUrl = URL_PREFIX + "/" + TEST_USER_ID + "/" + MISSION_ID + "/test-image.jpg";

            // when
            storageService.delete(imageUrl);

            // then
            assertThat(Files.exists(testFile)).isFalse();
        }

        @Test
        @DisplayName("LUT-400: 원본 삭제 시 thumb/medium 변형도 함께 삭제한다")
        void delete_removesVariantsToo() throws IOException {
            // given
            Path userDir = tempDir.resolve(TEST_USER_ID).resolve(String.valueOf(MISSION_ID));
            Files.createDirectories(userDir);
            Path originalFile = userDir.resolve("test-image.jpg");
            Path thumbFile = userDir.resolve("test-image_thumb.jpg");
            Path mediumFile = userDir.resolve("test-image_medium.jpg");
            Files.write(originalFile, "original".getBytes());
            Files.write(thumbFile, "thumb".getBytes());
            Files.write(mediumFile, "medium".getBytes());

            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);
            when(properties.getPath()).thenReturn(tempDir.toString());

            String imageUrl = URL_PREFIX + "/" + TEST_USER_ID + "/" + MISSION_ID + "/test-image.jpg";

            // when
            storageService.delete(imageUrl);

            // then
            assertThat(Files.exists(originalFile)).isFalse();
            assertThat(Files.exists(thumbFile)).isFalse();
            assertThat(Files.exists(mediumFile)).isFalse();
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

    // LUT-409: LUT-400 이전 업로드분(원본만 존재)에 thumb/medium 변형을 백필한다
    @Nested
    @DisplayName("backfillVariants 테스트")
    class BackfillVariantsTest {

        private static final byte[] ORIGINAL = "original-image".getBytes();
        private static final byte[] RESIZED = "resized-image".getBytes();

        private Path writeOriginal(String relativeDir, String filename) throws IOException {
            Path dir = tempDir.resolve(relativeDir);
            Files.createDirectories(dir);
            Path original = dir.resolve(filename);
            Files.write(original, ORIGINAL);
            return original;
        }

        @Test
        @DisplayName("변형이 없는 원본에 thumb/medium 2개를 생성한다")
        void backfill_createsBothVariants() throws IOException {
            when(properties.getPath()).thenReturn(tempDir.toString());
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);
            when(imageResizer.resize(any(byte[].class), any(), anyInt()))
                .thenReturn(Optional.of(RESIZED));
            writeOriginal("user1/1", "2024-01-15_abc.jpg");

            int created = storageService.backfillVariants(
                URL_PREFIX + "/user1/1/2024-01-15_abc.jpg");

            assertThat(created).isEqualTo(2);
            assertThat(tempDir.resolve("user1/1/2024-01-15_abc_thumb.jpg")).exists();
            assertThat(tempDir.resolve("user1/1/2024-01-15_abc_medium.jpg")).exists();
        }

        @Test
        @DisplayName("멱등: 변형이 모두 존재하면 아무것도 생성하지 않는다")
        void backfill_allVariantsExist_skips() throws IOException {
            when(properties.getPath()).thenReturn(tempDir.toString());
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);
            writeOriginal("user1/1", "2024-01-15_abc.jpg");
            Files.write(tempDir.resolve("user1/1/2024-01-15_abc_thumb.jpg"), RESIZED);
            Files.write(tempDir.resolve("user1/1/2024-01-15_abc_medium.jpg"), RESIZED);

            int created = storageService.backfillVariants(
                URL_PREFIX + "/user1/1/2024-01-15_abc.jpg");

            assertThat(created).isZero();
        }

        @Test
        @DisplayName("일부만 없으면 없는 변형만 생성한다")
        void backfill_partialVariants_createsMissingOnly() throws IOException {
            when(properties.getPath()).thenReturn(tempDir.toString());
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);
            when(imageResizer.resize(any(byte[].class), any(), anyInt()))
                .thenReturn(Optional.of(RESIZED));
            writeOriginal("user1/1", "2024-01-15_abc.jpg");
            Files.write(tempDir.resolve("user1/1/2024-01-15_abc_thumb.jpg"), RESIZED);

            int created = storageService.backfillVariants(
                URL_PREFIX + "/user1/1/2024-01-15_abc.jpg");

            assertThat(created).isEqualTo(1);
            assertThat(tempDir.resolve("user1/1/2024-01-15_abc_medium.jpg")).exists();
        }

        @Test
        @DisplayName("리사이즈 불가 포맷(GIF 등)은 변형 없이 0을 반환한다")
        void backfill_unresizableFormat_returnsZero() throws IOException {
            when(properties.getPath()).thenReturn(tempDir.toString());
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);
            when(imageResizer.resize(any(byte[].class), any(), anyInt()))
                .thenReturn(Optional.empty());
            writeOriginal("user1/1", "2024-01-15_abc.gif");

            int created = storageService.backfillVariants(
                URL_PREFIX + "/user1/1/2024-01-15_abc.gif");

            assertThat(created).isZero();
            assertThat(tempDir.resolve("user1/1/2024-01-15_abc_thumb.gif")).doesNotExist();
        }

        @Test
        @DisplayName("이 저장소가 서빙하지 않는 URL 은 대상에서 제외한다")
        void backfill_foreignUrl_returnsZero() {
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);

            int created = storageService.backfillVariants("https://cdn.example.com/missions/a.jpg");

            assertThat(created).isZero();
        }

        @Test
        @DisplayName("변형 URL 자체는 대상에서 제외한다 (방어)")
        void backfill_variantUrl_returnsZero() {
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);

            int created = storageService.backfillVariants(
                URL_PREFIX + "/user1/1/2024-01-15_abc_thumb.jpg");

            assertThat(created).isZero();
        }

        @Test
        @DisplayName("원본 파일이 없으면 예외를 던진다 (호출자가 실패로 집계)")
        void backfill_missingOriginal_throws() {
            when(properties.getPath()).thenReturn(tempDir.toString());
            when(properties.getUrlPrefix()).thenReturn(URL_PREFIX);

            assertThatThrownBy(() -> storageService.backfillVariants(
                URL_PREFIX + "/user1/1/missing.jpg"))
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
