package io.pinkspider.leveluptogethermvp.gamificationservice.shop.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalShopItemImageStorageServiceTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @TempDir
    Path tempDir;

    private ShopItemImageProperties properties;
    private LocalShopItemImageStorageService service;

    @BeforeEach
    void setUp() {
        properties = new ShopItemImageProperties();
        properties.setPath(tempDir.toString());
        properties.setUrlPrefix("/uploads/shop-items");
        service = new LocalShopItemImageStorageService(properties);
    }

    @Test
    @DisplayName("정상 PNG 파일을 저장하고 URL을 반환한다")
    void store_validPng_success() {
        MockMultipartFile file = new MockMultipartFile("file", "item.png", "image/png", PNG_BYTES);

        String url = service.store(file);

        assertThat(url).startsWith("/uploads/shop-items/").endsWith(".png");
    }

    @Test
    @DisplayName("확장자만 png인 가짜 이미지(시그니처 불일치)는 거부한다")
    void isValidImage_fakeImage_rejected() {
        MockMultipartFile fake = new MockMultipartFile(
            "file", "evil.png", "image/png", "<script>alert(1)</script>".getBytes());

        assertThat(service.isValidImage(fake)).isFalse();
    }

    @Test
    @DisplayName("경로 이탈(../) 삭제 요청은 거부한다")
    void delete_pathTraversal_rejected() throws IOException {
        // 업로드 루트 밖 파일 생성
        Path outside = tempDir.getParent().resolve("victim.txt");
        Files.writeString(outside, "important");

        service.delete("/uploads/shop-items/../victim.txt");

        assertThat(Files.exists(outside)).isTrue();
        Files.deleteIfExists(outside);
    }

    @Test
    @DisplayName("저장된 파일은 정상 삭제된다")
    void delete_storedFile_success() {
        MockMultipartFile file = new MockMultipartFile("file", "item.png", "image/png", PNG_BYTES);
        String url = service.store(file);
        String filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();

        service.delete(url);

        assertThat(Files.exists(tempDir.resolve(filename))).isFalse();
    }

    @Test
    @DisplayName("외부 URL 삭제 요청은 무시한다")
    void delete_externalUrl_ignored() {
        service.delete("https://external.example.com/x.png");
        // 예외 없이 무시되면 성공
    }
}
