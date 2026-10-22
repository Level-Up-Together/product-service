package io.pinkspider.leveluptogethermvp.chatservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** LUT-590: S3 채팅 이미지 저장소 — chats/ 키, 변형 동반 업로드, CDN URL, 삭제 */
@ExtendWith(MockitoExtension.class)
class S3ChatImageStorageServiceTest {

    private static final String CDN = "https://cdn.example.com";

    @Mock private S3Client s3Client;
    @Mock private ImageResizer imageResizer;

    private S3ChatImageStorageService storage;

    @BeforeEach
    void setUp() {
        S3ImageProperties s3Properties = new S3ImageProperties();
        s3Properties.setBucket("test-bucket");
        s3Properties.setCdnBaseUrl(CDN);
        ChatImageProperties properties = new ChatImageProperties();
        storage =
                new S3ChatImageStorageService(
                        s3Client,
                        s3Properties,
                        properties,
                        new ChatImageFileValidator(properties),
                        imageResizer);
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("image", "photo.jpg", "image/jpeg", "original".getBytes());
    }

    @Test
    @DisplayName("원본+변형 2개를 chats/{guild}/{user}/ 키로 올리고 CDN URL 을 돌려준다")
    void store_putsOriginalAndVariants() {
        when(imageResizer.resize(any(), eq("jpg"), anyInt()))
                .thenReturn(Optional.of("resized".getBytes()));

        String url = storage.store(jpeg(), 7L, "u1");

        assertThat(url).startsWith(CDN + "/chats/7/u1/").endsWith(".jpg");
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(3)).putObject(captor.capture(), any(RequestBody.class));
        List<String> keys = captor.getAllValues().stream().map(PutObjectRequest::key).toList();
        String key = url.substring(CDN.length() + 1);
        assertThat(keys)
                .containsExactly(
                        key, key.replace(".jpg", "_thumb.jpg"), key.replace(".jpg", "_medium.jpg"));
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().cacheControl()).contains("immutable");
    }

    @Test
    @DisplayName("변형 생성이 비거나 실패해도 원본 업로드는 성공한다")
    void store_variantFailureTolerated() {
        when(imageResizer.resize(any(), anyString(), eq(ImageResizer.THUMBNAIL_MAX_DIMENSION)))
                .thenReturn(Optional.empty());
        when(imageResizer.resize(any(), anyString(), eq(ImageResizer.MEDIUM_MAX_DIMENSION)))
                .thenThrow(new IllegalStateException("boom"));

        String url = storage.store(jpeg(), 7L, "u1");

        assertThat(url).startsWith(CDN + "/chats/7/u1/");
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("유효하지 않은 파일은 S3 에 올리지 않는다")
    void store_invalidFile() {
        MockMultipartFile text =
                new MockMultipartFile("image", "a.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> storage.store(text, 7L, "u1")).isInstanceOf(CustomException.class);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("파일 읽기 실패는 error.image.save_failed")
    void store_ioFailure() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getSize()).thenReturn(10L);
        when(broken.getOriginalFilename()).thenReturn("a.jpg");
        when(broken.getContentType()).thenReturn("image/jpeg");
        when(broken.getBytes()).thenThrow(new IOException("net"));

        assertThatThrownBy(() -> storage.store(broken, 7L, "u1"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.image.save_failed");
    }

    @Test
    @DisplayName("delete 는 원본·변형 3개를 지우고, 개별 실패는 삼킨다")
    void delete_removesAll() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("s3 down"));

        storage.delete(CDN + "/chats/7/u1/a.jpg");

        ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(3)).deleteObject(captor.capture());
        assertThat(captor.getAllValues().stream().map(DeleteObjectRequest::key))
                .containsExactly(
                        "chats/7/u1/a.jpg", "chats/7/u1/a_thumb.jpg", "chats/7/u1/a_medium.jpg");
    }

    @Test
    @DisplayName("다른 경로(미션 이미지 등)나 외부 URL 은 지우지 않는다")
    void delete_ignoresForeign() {
        storage.delete(CDN + "/missions/u1/1/a.jpg");
        storage.delete("https://evil.example.com/chats/a.jpg");
        storage.delete(null);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("isOwnedUrl: CDN/chats/ 로 시작하고 상위 경로(..)가 없어야 한다")
    void isOwnedUrl() {
        assertThat(storage.isOwnedUrl(CDN + "/chats/7/u1/a.jpg")).isTrue();
        assertThat(storage.isOwnedUrl(CDN + "/chats/../missions/a.jpg")).isFalse();
        assertThat(storage.isOwnedUrl(CDN + "/missions/a.jpg")).isFalse();
        assertThat(storage.isOwnedUrl(null)).isFalse();
    }
}
