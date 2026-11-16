package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.config.s3.S3ImageProperties;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageImageException;
import java.io.IOException;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("S3WidgetImageStorageService (LUT-797)")
class S3WidgetImageStorageServiceTest {
    private static final String CDN = "https://images.level-up-together.com";

    @Mock private S3Client s3Client;
    private S3WidgetImageStorageService storage;

    @BeforeEach
    void setUp() {
        S3ImageProperties s3Properties = new S3ImageProperties();
        s3Properties.setBucket("lut-images-prod");
        s3Properties.setCdnBaseUrl(CDN);
        storage =
                new S3WidgetImageStorageService(
                        s3Client, s3Properties, new WidgetImageProperties());
    }

    @Test
    @DisplayName("저장: widget/{uuid}.{ext} 키로 올리고 CDN 절대 URL 을 돌려준다")
    void store() {
        // 클라이언트가 붙인 Content-Type(svg)은 쓰지 않는다
        String url =
                storage.store(
                        new MockMultipartFile(
                                "file", "bg.JPG", "image/svg+xml", new byte[] {1, 2}));

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().key()).startsWith("widget/").endsWith(".jpg");
        assertThat(captor.getValue().bucket()).isEqualTo("lut-images-prod");
        assertThat(captor.getValue().cacheControl()).contains("immutable");
        assertThat(captor.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(WidgetImageFiles.contentTypeOf("png")).isEqualTo("image/png");
        assertThat(url).isEqualTo(CDN + "/" + captor.getValue().key());
        assertThat(storage.isOwned(url)).isTrue();
        assertThat(storage.isOwned(CDN + "/shop-items/x.png")).isFalse();
        assertThat(storage.isOwned(null)).isFalse();
    }

    @Test
    @DisplayName("저장: 형식이 안 맞으면 올리지 않고 080203 · 스트림 읽기 실패는 080204")
    void storeFailures() throws IOException {
        assertThatThrownBy(
                        () ->
                                storage.store(
                                        new MockMultipartFile(
                                                "file", "a.webp", "image/webp", new byte[] {1})))
                .isInstanceOfSatisfying(
                        WidgetMessageImageException.class,
                        e ->
                                assertThat(e.getCode())
                                        .isEqualTo(WidgetMessageImageException.CODE_INVALID));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getSize()).thenReturn(3L);
        when(broken.getOriginalFilename()).thenReturn("a.png");
        when(broken.getInputStream()).thenThrow(new IOException("disk"));
        assertThatThrownBy(() -> storage.store(broken))
                .isInstanceOfSatisfying(
                        WidgetMessageImageException.class,
                        e ->
                                assertThat(e.getCode())
                                        .isEqualTo(WidgetMessageImageException.CODE_SAVE_FAILED));
    }

    @Test
    @DisplayName("삭제: widget/ 키만 지우고 실패는 삼킨다 — 다른 접두어·경로 이탈·외부 URL 은 무시")
    void delete() {
        storage.delete(CDN + "/widget/a.png");
        ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("widget/a.png");

        storage.delete(CDN + "/shop-items/a.png");
        storage.delete(CDN + "/widget/../tower/a.png");
        storage.delete("https://evil.test/widget/a.png");
        storage.delete(null);
        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));

        doThrow(new IllegalStateException("s3 down"))
                .when(s3Client)
                .deleteObject(any(DeleteObjectRequest.class));
        storage.delete(CDN + "/widget/b.png");
    }
}
