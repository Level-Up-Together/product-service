package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageImageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("LocalWidgetImageStorageService (LUT-797)")
class LocalWidgetImageStorageServiceTest {
    @TempDir Path tempDir;
    private WidgetImageProperties properties;
    private LocalWidgetImageStorageService storage;

    @BeforeEach
    void setUp() {
        properties = new WidgetImageProperties();
        properties.setPath(tempDir.toString());
        properties.setPublicBaseUrl("https://dev.level-up-together.com:8443/");
        storage = new LocalWidgetImageStorageService(properties);
    }

    private static MockMultipartFile png(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "image/png", bytes);
    }

    @Test
    @DisplayName("저장: {uuid}.{ext} 로 쓰고 공개 주소 + 접두어로 절대 URL 을 돌려준다")
    void store() throws IOException {
        String url = storage.store(png("Morning Sky.PNG", new byte[] {1, 2, 3}));

        assertThat(url).startsWith("https://dev.level-up-together.com:8443/uploads/widget/");
        assertThat(url).endsWith(".png");
        String filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Files.readAllBytes(tempDir.resolve(filename))).containsExactly(1, 2, 3);
        assertThat(storage.isOwned(url)).isTrue();
        assertThat(storage.isOwned("https://cdn/other.png")).isFalse();
        assertThat(storage.isOwned(null)).isFalse();
    }

    @Test
    @DisplayName("공개 주소가 비면 상대 경로를 돌려준다 (경고 로그)")
    void storeWithoutBaseUrl() {
        properties.setPublicBaseUrl(null);
        assertThat(storage.store(png("a.png", new byte[] {1}))).startsWith("/uploads/widget/");
        properties.setPublicBaseUrl("  ");
        assertThat(storage.store(png("a.png", new byte[] {1}))).startsWith("/uploads/widget/");
    }

    @Test
    @DisplayName("거절: 빈 파일·크기 초과·확장자 불허·확장자 없음·이름 없음 → 080203")
    void rejects() {
        assertInvalid(png("a.png", new byte[0]));
        assertInvalid(null);
        assertInvalid(png("a.gif", new byte[] {1}));
        assertInvalid(png("noext", new byte[] {1}));
        assertInvalid(png("dot.", new byte[] {1}));
        assertInvalid(png(" ", new byte[] {1}));
        assertInvalid(new MockMultipartFile("file", null, "image/png", new byte[] {1}));
        properties.setMaxSize(2);
        assertInvalid(png("a.png", new byte[] {1, 2, 3}));
    }

    @Test
    @DisplayName("저장 실패(쓸 수 없는 경로)는 080204")
    void saveFailed() throws IOException {
        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "x");
        properties.setPath(blocker.toString());

        assertThatThrownBy(() -> storage.store(png("a.png", new byte[] {1})))
                .isInstanceOfSatisfying(
                        WidgetMessageImageException.class,
                        e ->
                                assertThat(e.getCode())
                                        .isEqualTo(WidgetMessageImageException.CODE_SAVE_FAILED));
    }

    @Test
    @DisplayName("삭제: 우리가 발급한 파일만 지운다 — 외부 주소·경로 이탈·하위 폴더는 무시, 없는 파일은 조용히")
    void delete() throws IOException {
        String url = storage.store(png("a.png", new byte[] {1}));
        Path stored = tempDir.resolve(url.substring(url.lastIndexOf('/') + 1));
        Path outside = Files.writeString(tempDir.getParent().resolve("outside.png"), "x");
        String prefix = "https://dev.level-up-together.com:8443/uploads/widget/";

        storage.delete("https://evil.test/a.png");
        storage.delete(null);
        storage.delete(prefix + "../outside.png");
        storage.delete(prefix + "sub/a.png");
        storage.delete(prefix + "a\\b.png");
        storage.delete(prefix);
        storage.delete(prefix + "missing.png");
        assertThat(stored).exists();
        assertThat(outside).exists();

        storage.delete(url);
        assertThat(stored).doesNotExist();
        Files.deleteIfExists(outside);
    }

    private void assertInvalid(MultipartFile file) {
        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOfSatisfying(
                        WidgetMessageImageException.class,
                        e ->
                                assertThat(e.getCode())
                                        .isEqualTo(WidgetMessageImageException.CODE_INVALID));
    }
}
