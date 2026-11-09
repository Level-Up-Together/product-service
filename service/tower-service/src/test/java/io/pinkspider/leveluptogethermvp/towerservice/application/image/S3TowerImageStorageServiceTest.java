package io.pinkspider.leveluptogethermvp.towerservice.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.config.s3.S3ImageProperties;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;
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
@DisplayName("S3TowerImageStorageService (LUT-635)")
class S3TowerImageStorageServiceTest {
    @Mock private S3Client s3Client;
    private S3ImageProperties s3Properties;
    private S3TowerImageStorageService storage;

    @BeforeEach
    void setUp() {
        s3Properties = new S3ImageProperties();
        s3Properties.setBucket("lut-images-prod");
        s3Properties.setCdnBaseUrl("https://images.level-up-together.com");
        storage =
                new S3TowerImageStorageService(s3Client, s3Properties, new TowerImageProperties());
    }

    @Test
    @DisplayName("저장: tower/{kind}/{uuid}.{ext} 키로 올리고 CDN 절대 URL 을 돌려준다")
    void store() {
        String url =
                storage.store(
                        new MockMultipartFile("file", "bg.JPG", "image/jpeg", new byte[] {1, 2}),
                        TowerImageKind.BACKGROUND);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().key()).startsWith("tower/background/").endsWith(".jpg");
        assertThat(captor.getValue().bucket()).isEqualTo("lut-images-prod");
        assertThat(captor.getValue().cacheControl()).contains("immutable");
        assertThat(url)
                .isEqualTo("https://images.level-up-together.com/" + captor.getValue().key());
        assertThat(storage.isOwned(url)).isTrue();
        assertThat(storage.isOwned("https://images.level-up-together.com/shop-items/x.png"))
                .isFalse();
    }

    @Test
    @DisplayName("저장: 스트림 읽기 실패는 150111(서버 오류)")
    void storeIoFailure() throws IOException {
        MultipartFile broken = org.mockito.Mockito.mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getSize()).thenReturn(10L);
        when(broken.getOriginalFilename()).thenReturn("mask.png");
        when(broken.getInputStream()).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> storage.store(broken, TowerImageKind.MASK))
                .isInstanceOf(TowerFloorException.class)
                .hasFieldOrPropertyWithValue("code", TowerFloorException.IMAGE_SAVE_FAILED);
    }

    @Test
    @DisplayName("삭제: tower/ 키만 지우고 실패는 삼킨다, 다른 접두어·경로 이탈·외부 URL 은 무시")
    void delete() {
        storage.delete("https://images.level-up-together.com/tower/mask/a.png");
        ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("tower/mask/a.png");

        doThrow(new RuntimeException("s3 down"))
                .when(s3Client)
                .deleteObject(any(DeleteObjectRequest.class));
        storage.delete("https://images.level-up-together.com/tower/mask/b.png"); // warn 만

        storage.delete("https://images.level-up-together.com/tower/../shop-items/x.png");
        storage.delete("https://images.level-up-together.com/shop-items/x.png");
        storage.delete("https://dev.level-up-together.com:8443/uploads/tower/mask/a.png");
        storage.delete(null);
        verify(s3Client, never())
                .deleteObject(
                        DeleteObjectRequest.builder()
                                .bucket("lut-images-prod")
                                .key("shop-items/x.png")
                                .build());
    }

    @Test
    @DisplayName("LUT-727 생성 파일: tower/pixel/{이름} 키로 올리고 CDN URL · S3 오류는 150111 · 경로 문자 이름 거부")
    void storeGenerated() {
        String url =
                storage.storeGenerated(
                        new byte[] {1, 2}, TowerImageKind.PIXEL, "abcd.png", "image/png");
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().key()).isEqualTo("tower/pixel/abcd.png");
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo("https://images.level-up-together.com/tower/pixel/abcd.png");

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new IllegalStateException("s3 down"));
        assertThatThrownBy(
                        () ->
                                storage.storeGenerated(
                                        new byte[] {1}, TowerImageKind.PIXEL, "x.png", "image/png"))
                .isInstanceOf(TowerFloorException.class)
                .hasFieldOrPropertyWithValue("code", TowerFloorException.IMAGE_SAVE_FAILED);
        assertThatThrownBy(
                        () ->
                                storage.storeGenerated(
                                        new byte[] {1},
                                        TowerImageKind.PIXEL,
                                        "a/b.png",
                                        "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
