package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.component.ImageResizer;
import io.pinkspider.global.config.s3.S3ImageProperties;
import io.pinkspider.global.exception.CustomException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3MissionImageStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private ImageResizer imageResizer;

    private S3ImageProperties s3Properties;
    private MissionImageProperties missionImageProperties;
    private S3MissionImageStorageService storageService;

    private static final String TEST_USER_ID = "test-user-123";
    private static final Long MISSION_ID = 1L;
    private static final String EXECUTION_DATE = "2024-01-15";

    @BeforeEach
    void setUp() {
        s3Properties = new S3ImageProperties();
        s3Properties.setBucket("test-bucket");
        s3Properties.setCdnBaseUrl("https://cdn.example.com");

        missionImageProperties = new MissionImageProperties();
        missionImageProperties.setMaxSize(5242880L);
        missionImageProperties.setAllowedExtensions("jpg,jpeg,png,gif,webp");

        storageService = new S3MissionImageStorageService(
            s3Client, s3Properties, missionImageProperties, imageResizer
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
                "file", "test.jpg", "image/jpeg", "test image content".getBytes()
            );
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            // when
            String result = storageService.store(file, TEST_USER_ID, MISSION_ID, EXECUTION_DATE);

            // then
            assertThat(result).startsWith("https://cdn.example.com/missions/" + TEST_USER_ID + "/" + MISSION_ID + "/");
            assertThat(result).endsWith(".jpg");
            // LUT-406: 업로드 객체에 immutable 캐시 메타데이터가 실려야 CloudFront/브라우저가 캐시한다
            ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client, times(1)).putObject(putCaptor.capture(), any(RequestBody.class));
            assertThat(putCaptor.getValue().cacheControl())
                .isEqualTo("public, max-age=31536000, immutable");
        }

        @Test
        @DisplayName("null 파일이면 예외가 발생한다")
        void store_nullFile_throwsException() {
            assertThatThrownBy(() -> storageService.store(null, TEST_USER_ID, MISSION_ID, EXECUTION_DATE))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "MISSION_IMAGE_001");
        }

        @Test
        @DisplayName("허용되지 않은 확장자면 예외가 발생한다")
        void store_invalidExtension_throwsException() {
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.exe", "application/octet-stream", "content".getBytes()
            );

            assertThatThrownBy(() -> storageService.store(file, TEST_USER_ID, MISSION_ID, EXECUTION_DATE))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("code", "MISSION_IMAGE_002");
        }

        @Test
        @DisplayName("LUT-400: 리사이즈 변형이 생성되면 원본을 포함해 총 3개 오브젝트를 업로드한다")
        void store_withVariants_uploadsThreeObjects() throws IOException {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "test image content".getBytes()
            );
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
            when(imageResizer.resize(any(byte[].class), eq("jpg"), anyInt()))
                .thenReturn(Optional.of("resized-bytes".getBytes()));

            // when
            storageService.store(file, TEST_USER_ID, MISSION_ID, EXECUTION_DATE);

            // then: 원본 + thumb + medium
            verify(s3Client, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
            verify(imageResizer).resize(any(byte[].class), eq("jpg"), eq(ImageResizer.THUMBNAIL_MAX_DIMENSION));
            verify(imageResizer).resize(any(byte[].class), eq("jpg"), eq(ImageResizer.MEDIUM_MAX_DIMENSION));
        }

        @Test
        @DisplayName("LUT-400: 변형 생성이 비어있으면 원본만 업로드한다")
        void store_variantEmpty_uploadsOriginalOnly() throws IOException {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "test image content".getBytes()
            );
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
            when(imageResizer.resize(any(byte[].class), anyString(), anyInt()))
                .thenReturn(Optional.empty());

            // when
            storageService.store(file, TEST_USER_ID, MISSION_ID, EXECUTION_DATE);

            // then
            verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("LUT-400: 변형 생성 중 예외가 발생해도 원본 업로드는 성공한다")
        void store_variantThrows_originalStillUploaded() throws IOException {
            // given
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "test image content".getBytes()
            );
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
            when(imageResizer.resize(any(byte[].class), anyString(), anyInt()))
                .thenThrow(new RuntimeException("리사이즈 실패"));

            // when
            String result = storageService.store(file, TEST_USER_ID, MISSION_ID, EXECUTION_DATE);

            // then
            assertThat(result).isNotNull();
            verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }
    }

    @Nested
    @DisplayName("delete 테스트")
    class DeleteTest {

        @Test
        @DisplayName("LUT-400: 원본과 함께 thumb/medium 변형도 삭제를 시도한다")
        void delete_deletesOriginalAndVariants() {
            // given
            String imageUrl = "https://cdn.example.com/missions/user1/1/2024-01-15_uuid.jpg";
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

            // when
            storageService.delete(imageUrl);

            // then: 원본 + thumb + medium = 3회
            verify(s3Client, times(3)).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("null URL이면 아무것도 하지 않는다")
        void delete_nullUrl_doesNothing() {
            storageService.delete(null);
        }

        @Test
        @DisplayName("변형 삭제 중 예외가 발생해도 나머지 삭제는 계속 진행된다")
        void delete_partialFailure_continuesDeleting() {
            // given
            String imageUrl = "https://cdn.example.com/missions/user1/1/2024-01-15_uuid.jpg";
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 연결 실패"))
                .thenReturn(DeleteObjectResponse.builder().build());

            // when & then: 예외가 밖으로 전파되지 않는다
            storageService.delete(imageUrl);
            verify(s3Client, times(3)).deleteObject(any(DeleteObjectRequest.class));
        }
    }

    @Nested
    @DisplayName("isValidImage 테스트")
    class IsValidImageTest {

        @Test
        @DisplayName("유효한 이미지면 true를 반환한다")
        void isValidImage_valid_returnsTrue() {
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "content".getBytes()
            );

            assertThat(storageService.isValidImage(file)).isTrue();
        }

        @Test
        @DisplayName("허용되지 않은 확장자면 false를 반환한다")
        void isValidImage_invalidExtension_returnsFalse() {
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.bmp", "image/bmp", "content".getBytes()
            );

            assertThat(storageService.isValidImage(file)).isFalse();
        }
    }

    // LUT-409: LUT-400 이전 업로드분(원본만 존재)에 thumb/medium 변형을 백필한다
    @Nested
    @DisplayName("backfillVariants 테스트")
    class BackfillVariantsTest {

        private static final String KEY = "missions/user1/1/2024-01-15_abc.jpg";
        private static final String CDN_URL = "https://cdn.example.com/" + KEY;
        private static final byte[] ORIGINAL = "original-image".getBytes();
        private static final byte[] RESIZED = "resized-image".getBytes();

        private void stubOriginalDownload() {
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                    GetObjectResponse.builder().contentType("image/jpeg").build(), ORIGINAL));
        }

        @Test
        @DisplayName("변형이 없는 원본에 thumb/medium 2개를 생성하고 immutable 캐시 메타데이터를 싣는다")
        void backfill_createsBothVariants() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());
            stubOriginalDownload();
            when(imageResizer.resize(any(byte[].class), any(), anyInt()))
                .thenReturn(Optional.of(RESIZED));
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            int created = storageService.backfillVariants(CDN_URL);

            assertThat(created).isEqualTo(2);
            ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client, times(2)).putObject(putCaptor.capture(), any(RequestBody.class));
            assertThat(putCaptor.getAllValues())
                .extracting(PutObjectRequest::key)
                .containsExactly(
                    "missions/user1/1/2024-01-15_abc_thumb.jpg",
                    "missions/user1/1/2024-01-15_abc_medium.jpg");
            assertThat(putCaptor.getAllValues())
                .allSatisfy(put -> assertThat(put.cacheControl())
                    .isEqualTo("public, max-age=31536000, immutable"));
            // 원본은 1회만 내려받는다
            verify(s3Client, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
        }

        @Test
        @DisplayName("멱등: 변형이 모두 존재하면 원본 다운로드도 업로드도 하지 않는다")
        void backfill_allVariantsExist_skips() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

            int created = storageService.backfillVariants(CDN_URL);

            assertThat(created).isZero();
            verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
            verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("리사이즈 불가 포맷은 변형 없이 0을 반환한다")
        void backfill_unresizableFormat_returnsZero() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());
            stubOriginalDownload();
            when(imageResizer.resize(any(byte[].class), any(), anyInt()))
                .thenReturn(Optional.empty());

            int created = storageService.backfillVariants(CDN_URL);

            assertThat(created).isZero();
            verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("CDN 이 서빙하지 않는 URL(로컬 /uploads 등)은 대상에서 제외한다")
        void backfill_foreignUrl_returnsZero() {
            int created = storageService.backfillVariants("/uploads/missions/user1/1/a.jpg");

            assertThat(created).isZero();
            verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
        }

        @Test
        @DisplayName("변형 URL 자체는 대상에서 제외한다 (방어)")
        void backfill_variantUrl_returnsZero() {
            int created = storageService.backfillVariants(
                "https://cdn.example.com/missions/user1/1/2024-01-15_abc_thumb.jpg");

            assertThat(created).isZero();
            verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
        }
    }
}
