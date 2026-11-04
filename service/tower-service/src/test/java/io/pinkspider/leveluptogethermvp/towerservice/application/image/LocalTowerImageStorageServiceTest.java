package io.pinkspider.leveluptogethermvp.towerservice.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("LocalTowerImageStorageService (LUT-635)")
class LocalTowerImageStorageServiceTest {
    @TempDir Path tempDir;
    private TowerImageProperties properties;
    private LocalTowerImageStorageService storage;

    @BeforeEach
    void setUp() {
        properties = new TowerImageProperties();
        properties.setPath(tempDir.toString());
        properties.setPublicBaseUrl("https://dev.level-up-together.com:8443/");
        storage = new LocalTowerImageStorageService(properties);
    }

    @Test
    @DisplayName("저장: {kind}/{uuid}.{ext} 에 쓰고 공개 주소 + 접두어로 절대 URL 을 돌려준다")
    void store() throws IOException {
        String url =
                storage.store(
                        new MockMultipartFile(
                                "file", "Mask Final.PNG", "image/png", new byte[] {1, 2, 3}),
                        TowerImageKind.MASK);

        assertThat(url).startsWith("https://dev.level-up-together.com:8443/uploads/tower/mask/");
        assertThat(url).endsWith(".png");
        String filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Files.readAllBytes(tempDir.resolve("mask").resolve(filename)))
                .containsExactly(1, 2, 3);
        assertThat(storage.isOwned(url)).isTrue();
        assertThat(storage.isOwned("https://cdn/other.png")).isFalse();
    }

    @Test
    @DisplayName("공개 주소가 비면 상대 경로를 돌려준다 (층 API 가 거절하도록 경고)")
    void storeWithoutBaseUrl() {
        properties.setPublicBaseUrl(null);
        String url =
                storage.store(
                        new MockMultipartFile("file", "bg.jpg", "image/jpeg", new byte[] {1}),
                        TowerImageKind.BACKGROUND);
        assertThat(url).startsWith("/uploads/tower/background/");
    }

    @Test
    @DisplayName("거절: 빈 파일·크기 초과·확장자 불허·확장자 없음 → 150110")
    void rejects() {
        assertThatThrownBy(
                        () ->
                                storage.store(
                                        new MockMultipartFile(
                                                "file", "a.png", "image/png", new byte[0]),
                                        TowerImageKind.MASK))
                .isInstanceOf(TowerFloorException.class)
                .hasFieldOrPropertyWithValue("code", TowerFloorException.IMAGE_INVALID);
        properties.setMaxSize(2);
        assertThatThrownBy(
                        () ->
                                storage.store(
                                        new MockMultipartFile(
                                                "file", "a.png", "image/png", new byte[] {1, 2, 3}),
                                        TowerImageKind.MASK))
                .hasFieldOrPropertyWithValue("code", TowerFloorException.IMAGE_INVALID);
        properties.setMaxSize(1000);
        assertThatThrownBy(
                        () ->
                                storage.store(
                                        new MockMultipartFile(
                                                "file", "a.gif", "image/gif", new byte[] {1}),
                                        TowerImageKind.BACKGROUND))
                .hasFieldOrPropertyWithValue("code", TowerFloorException.IMAGE_INVALID);
        assertThatThrownBy(
                        () ->
                                storage.store(
                                        new MockMultipartFile(
                                                "file", "noext", "image/png", new byte[] {1}),
                                        TowerImageKind.BACKGROUND))
                .hasFieldOrPropertyWithValue("code", TowerFloorException.IMAGE_INVALID);
        assertThatThrownBy(
                        () ->
                                storage.store(
                                        new MockMultipartFile(
                                                "file", "", "image/png", new byte[] {1}),
                                        TowerImageKind.BACKGROUND))
                .hasFieldOrPropertyWithValue("code", TowerFloorException.IMAGE_INVALID);
    }

    @Test
    @DisplayName("삭제: 발급한 URL 만 지우고, 외부 URL·경로 이탈·다른 깊이는 무시한다")
    void delete() throws IOException {
        String url =
                storage.store(
                        new MockMultipartFile("file", "bg.webp", "image/webp", new byte[] {9}),
                        TowerImageKind.BACKGROUND);
        String filename = url.substring(url.lastIndexOf('/') + 1);
        Path stored = tempDir.resolve("background").resolve(filename);
        assertThat(stored).exists();

        Path other = tempDir.resolve("keep.txt");
        Files.write(other, new byte[] {1});
        storage.delete(
                "https://dev.level-up-together.com:8443/uploads/tower/background/../keep.txt");
        storage.delete("https://dev.level-up-together.com:8443/uploads/tower/keep.txt");
        storage.delete("https://dev.level-up-together.com:8443/uploads/tower/a/b/c.png");
        storage.delete("https://cdn.example.com/tower/background/x.png");
        storage.delete(null);
        assertThat(other).exists();
        assertThat(stored).exists();

        storage.delete(url);
        assertThat(stored).doesNotExist();
        storage.delete(url); // 없는 파일은 조용히
    }

    @Test
    @DisplayName("LUT-637: BGM 은 bgm/ 폴더에 mp3·aac·m4a 만, 10MB 초과·이미지 확장자는 150113")
    void storeBgm() throws IOException {
        String url =
                storage.store(
                        new MockMultipartFile("file", "Loop.MP3", "audio/mpeg", new byte[] {1, 2}),
                        TowerImageKind.BGM);
        assertThat(url).startsWith("https://dev.level-up-together.com:8443/uploads/tower/bgm/");
        assertThat(url).endsWith(".mp3");
        assertThat(storage.isOwned(url)).isTrue();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                storage.store(
                                        new MockMultipartFile(
                                                "file", "bg.png", "image/png", new byte[] {1}),
                                        TowerImageKind.BGM))
                .extracting("code")
                .isEqualTo(
                        io.pinkspider.leveluptogethermvp.towerservice.application.exception
                                .TowerFloorException.AUDIO_INVALID);

        properties.setAudioMaxSize(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                storage.store(
                                        new MockMultipartFile(
                                                "file", "big.m4a", "audio/mp4", new byte[] {1, 2}),
                                        TowerImageKind.BGM))
                .extracting("code")
                .isEqualTo(
                        io.pinkspider.leveluptogethermvp.towerservice.application.exception
                                .TowerFloorException.AUDIO_INVALID);
    }
}
