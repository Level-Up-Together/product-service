package io.pinkspider.leveluptogethermvp.chatservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.component.ImageResizer;
import io.pinkspider.global.exception.CustomException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** LUT-590: 로컬 채팅 이미지 저장소 — 원본+변형 저장, 삭제, 소유 URL 판정 */
@ExtendWith(MockitoExtension.class)
class LocalChatImageStorageServiceTest {

    @TempDir Path tempDir;

    @Mock private ImageResizer imageResizer;

    private ChatImageProperties properties;
    private LocalChatImageStorageService storage;

    @BeforeEach
    void setUp() {
        properties = new ChatImageProperties();
        properties.setPath(tempDir.toString());
        storage =
                new LocalChatImageStorageService(
                        properties, new ChatImageFileValidator(properties), imageResizer);
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("image", "photo.jpg", "image/jpeg", "original".getBytes());
    }

    @Test
    @DisplayName("원본과 _thumb·_medium 변형을 길드/유저 경로에 저장하고 원본 URL 을 돌려준다")
    void store_writesOriginalAndVariants() throws IOException {
        when(imageResizer.resize(any(), eq("jpg"), anyInt()))
                .thenReturn(Optional.of("resized".getBytes()));

        String url = storage.store(jpeg(), 7L, "u1");

        assertThat(url).startsWith("/uploads/chats/7/u1/").endsWith(".jpg");
        String relative = url.substring("/uploads/chats".length());
        Path original = Path.of(tempDir.toString(), relative);
        assertThat(Files.readString(original)).isEqualTo("original");
        assertThat(Files.exists(Path.of(original.toString().replace(".jpg", "_thumb.jpg"))))
                .isTrue();
        assertThat(Files.exists(Path.of(original.toString().replace(".jpg", "_medium.jpg"))))
                .isTrue();
    }

    @Test
    @DisplayName("리사이즈 불가(빈 결과)·리사이즈 예외여도 원본은 저장된다 (best-effort)")
    void store_variantFailureKeepsOriginal() {
        when(imageResizer.resize(any(), anyString(), eq(ImageResizer.THUMBNAIL_MAX_DIMENSION)))
                .thenReturn(Optional.empty());
        when(imageResizer.resize(any(), anyString(), eq(ImageResizer.MEDIUM_MAX_DIMENSION)))
                .thenThrow(new IllegalStateException("boom"));

        String url = storage.store(jpeg(), 7L, "u1");

        assertThat(Files.exists(Path.of(tempDir.toString(), url.substring(14)))).isTrue();
    }

    @Test
    @DisplayName("유효하지 않은 파일은 저장하지 않고 예외")
    void store_invalidFile() {
        MockMultipartFile text =
                new MockMultipartFile("image", "a.txt", "text/plain", "x".getBytes());
        assertThatThrownBy(() -> storage.store(text, 7L, "u1")).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("파일 읽기 실패는 error.image.save_failed")
    void store_ioFailure() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getSize()).thenReturn(10L);
        when(broken.getOriginalFilename()).thenReturn("a.jpg");
        when(broken.getContentType()).thenReturn("image/jpeg");
        when(broken.getBytes()).thenThrow(new IOException("disk"));

        assertThatThrownBy(() -> storage.store(broken, 7L, "u1"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("error.image.save_failed");
    }

    @Test
    @DisplayName("delete 는 원본과 변형을 모두 지우고, 남의 URL 은 무시한다")
    void delete_removesOriginalAndVariants() throws IOException {
        when(imageResizer.resize(any(), anyString(), anyInt()))
                .thenReturn(Optional.of("resized".getBytes()));
        String url = storage.store(jpeg(), 7L, "u1");
        Path dir = Path.of(tempDir.toString(), "7", "u1");
        assertThat(Files.list(dir).count()).isEqualTo(3);

        storage.delete("https://evil.example.com/x.jpg");
        storage.delete(null);
        assertThat(Files.list(dir).count()).isEqualTo(3);

        storage.delete(url);
        assertThat(Files.list(dir).count()).isZero();
    }

    @Test
    @DisplayName("isOwnedBy: 그 길드·그 유저 경로여야 하고 상위 경로(..)는 거부")
    void isOwnedBy() {
        assertThat(storage.isOwnedBy("/uploads/chats/7/u1/a.jpg", 7L, "u1")).isTrue();
        assertThat(storage.isOwnedBy("/uploads/chats/7/u2/a.jpg", 7L, "u1")).isFalse();
        assertThat(storage.isOwnedBy("/uploads/chats/8/u1/a.jpg", 7L, "u1")).isFalse();
        assertThat(storage.isOwnedBy("/uploads/chats/7/u1x/a.jpg", 7L, "u1")).isFalse();
        assertThat(storage.isOwnedBy("/uploads/chats/7/u1/../../8/u2/a.jpg", 7L, "u1")).isFalse();
        assertThat(storage.isOwnedBy("/uploads/missions/7/u1/a.jpg", 7L, "u1")).isFalse();
        assertThat(storage.isOwnedBy("https://evil.example.com/a.jpg", 7L, "u1")).isFalse();
        assertThat(storage.isOwnedBy(null, 7L, "u1")).isFalse();
        assertThat(storage.isOwnedBy("/uploads/chats/7/u1/a.jpg", null, "u1")).isFalse();
        assertThat(storage.isOwnedBy("/uploads/chats/7/u1/a.jpg", 7L, null)).isFalse();
    }
}
