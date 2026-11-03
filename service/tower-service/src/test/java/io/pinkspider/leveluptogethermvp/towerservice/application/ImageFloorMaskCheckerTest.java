package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** LUT-629: 마스크 PNG 픽셀로 입장 지점 판정 */
class ImageFloorMaskCheckerTest {

    @TempDir Path dir;

    // 테스트는 file: URL 로 읽는다 (원격 검증은 아래 ssrfGuard 에서)
    private final ImageFloorMaskChecker checker =
            new ImageFloorMaskChecker(List.of("images.example.com"), true);
    private String maskUrl;
    private String backgroundUrl;

    @BeforeEach
    void setUp() throws IOException {
        // 10×10 마스크: (2..5, 2..5) 만 붉은색, 나머지 투명
        BufferedImage mask = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        for (int x = 2; x <= 5; x++) {
            for (int y = 2; y <= 5; y++) {
                mask.setRGB(x, y, new Color(220, 30, 30, 255).getRGB());
            }
        }
        File maskFile = dir.resolve("mask.png").toFile();
        ImageIO.write(mask, "png", maskFile);
        maskUrl = maskFile.toURI().toString();

        // 배경은 마스크의 2배 해상도(20×20)
        BufferedImage bg = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        File bgFile = dir.resolve("bg.png").toFile();
        ImageIO.write(bg, "png", bgFile);
        backgroundUrl = bgFile.toURI().toString();
    }

    @Test
    @DisplayName("배경 없이: 붉은 픽셀 안이면 true, 밖이면 false, 범위 밖도 false")
    void withoutBackground() throws IOException {
        assertThat(checker.isWalkable(maskUrl, null, 3, 3)).isTrue();
        assertThat(checker.isWalkable(maskUrl, null, 8, 8)).isFalse();
        assertThat(checker.isWalkable(maskUrl, null, 10, 3)).isFalse();
        assertThat(checker.isWalkable(maskUrl, null, -1, 3)).isFalse();
    }

    @Test
    @DisplayName("배경이 마스크보다 크면 비율로 옮겨 판정한다 (배경 (6,6) → 마스크 (3,3))")
    void scaledByBackground() throws IOException {
        assertThat(checker.isWalkable(maskUrl, backgroundUrl, 6, 6)).isTrue();
        assertThat(checker.isWalkable(maskUrl, backgroundUrl, 16, 16)).isFalse();
        assertThat(checker.isWalkable(maskUrl, backgroundUrl, 20, 6)).isFalse();
    }

    @Test
    @DisplayName("배경을 못 읽으면 마스크 좌표계로 판정하고, 마스크를 못 읽으면 IOException")
    void unreadable() throws IOException {
        assertThat(checker.isWalkable(maskUrl, dir.resolve("none.png").toUri().toString(), 3, 3))
                .isTrue();
        assertThatThrownBy(
                        () ->
                                checker.isWalkable(
                                        dir.resolve("missing.png").toUri().toString(), null, 3, 3))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("붉은색 판정: R 높고 G·B 낮고 불투명해야 한다")
    void isRed() {
        assertThat(ImageFloorMaskChecker.isRed(new Color(255, 0, 0, 255).getRGB())).isTrue();
        assertThat(ImageFloorMaskChecker.isRed(new Color(255, 0, 0, 0).getRGB())).isFalse();
        assertThat(ImageFloorMaskChecker.isRed(new Color(255, 200, 200, 255).getRGB())).isFalse();
        assertThat(ImageFloorMaskChecker.isRed(new Color(100, 0, 0, 255).getRGB())).isFalse();
    }

    @Test
    @DisplayName("SSRF 방어: https 아님·허용 목록 밖 호스트·루프백으로 풀리는 호스트는 읽지 않는다")
    void ssrfGuard() {
        ImageFloorMaskChecker remote =
                new ImageFloorMaskChecker(List.of("images.example.com", "LOCALHOST."), false);
        assertThatThrownBy(
                        () -> remote.validateRemote(URI.create("http://images.example.com/m.png")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("https");
        assertThatThrownBy(
                        () -> remote.validateRemote(URI.create("https://evil.example.org/m.png")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not allowed");
        // 허용 목록에 있어도 사설·루프백 주소로 풀리면 거부 (DNS 리바인딩)
        assertThatThrownBy(() -> remote.validateRemote(URI.create("https://localhost/m.png")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("private");
        // file: 은 테스트 생성자에서만 허용 — 운영 빈은 거부
        assertThatThrownBy(() -> remote.isWalkable(maskUrl, null, 3, 3))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("호스트 정규화: 소문자·끝 점 제거")
    void normalizeHost() {
        assertThat(ImageFloorMaskChecker.normalizeHost("Images.Example.COM."))
                .isEqualTo("images.example.com");
        assertThat(ImageFloorMaskChecker.normalizeHost(null)).isNull();
    }
}
