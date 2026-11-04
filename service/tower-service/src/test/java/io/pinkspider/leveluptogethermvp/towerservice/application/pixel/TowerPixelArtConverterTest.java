package io.pinkspider.leveluptogethermvp.towerservice.application.pixel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.leveluptogethermvp.towerservice.application.pixel.TowerPixelArtConverter.Result;
import io.pinkspider.leveluptogethermvp.towerservice.application.pixel.TowerPixelArtConverter.Settings;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LUT-727: 픽셀아트 변환 — 결정성·해상도·팔레트·외톨이 정리 */
class TowerPixelArtConverterTest {

    private static final TowerPixelArtConverter CONVERTER = new TowerPixelArtConverter();
    private static BufferedImage source;
    private static Result defaults;

    /** 색 블록 + 붓 터치 같은 잡음 (작은 크기 — 빠르게) */
    private static BufferedImage painting(int w, int h, long seed) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int block = ((x / 20) * 7 + (y / 15) * 13) % 9;
                int r = 40 + block * 22 + random.nextInt(16);
                int g = 200 - block * 18 + random.nextInt(16);
                int b = (block * 47) % 200 + random.nextInt(16);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    @BeforeAll
    static void convertOnce() {
        source = painting(176, 96, 1);
        defaults = CONVERTER.convert(source, Settings.defaults());
    }

    /** PNG 의 PLTE 청크 항목 수 */
    private static int paletteEntries(byte[] png) {
        for (int i = 8; i + 8 < png.length; ) {
            int length =
                    ((png[i] & 0xff) << 24)
                            | ((png[i + 1] & 0xff) << 16)
                            | ((png[i + 2] & 0xff) << 8)
                            | (png[i + 3] & 0xff);
            String type = new String(png, i + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (type.equals("PLTE")) {
                return length / 3;
            }
            i += 12 + length;
        }
        return -1;
    }

    private static BufferedImage read(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    @DisplayName("해상도: 높이는 576·384 로 고정, 가로는 원본 비율 (2816×1536 → 1056·704, 1376×768 → 1032·688)")
    void targetWidth() {
        assertThat(TowerPixelArtConverter.targetWidth(2816, 1536, 576)).isEqualTo(1056);
        assertThat(TowerPixelArtConverter.targetWidth(2816, 1536, 384)).isEqualTo(704);
        assertThat(TowerPixelArtConverter.targetWidth(1376, 768, 576)).isEqualTo(1032);
        assertThat(TowerPixelArtConverter.targetWidth(1376, 768, 384)).isEqualTo(688);
        assertThat(TowerPixelArtConverter.targetWidth(1, 5000, 384)).isEqualTo(1);
        assertThatThrownBy(() -> TowerPixelArtConverter.targetWidth(0, 10, 384))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("결과: 원본 크기와 무관하게 높이 576·384 의 64색 이하 인덱스 PNG, 버전은 내용 해시 16자리")
    void outputs() throws IOException {
        assertThat(defaults.dense().height()).isEqualTo(576);
        assertThat(defaults.dense().width()).isEqualTo(1056);
        assertThat(defaults.standard().height()).isEqualTo(384);
        assertThat(defaults.standard().width()).isEqualTo(704);
        BufferedImage dense = read(defaults.dense().png());
        assertThat(dense.getWidth()).isEqualTo(1056);
        assertThat(dense.getHeight()).isEqualTo(576);
        assertThat(dense.getColorModel()).isInstanceOf(IndexColorModel.class);
        // 파일의 팔레트(PLTE) 는 64색 이하 (읽을 때 Java 가 256칸으로 채우므로 바이트로 본다)
        assertThat(paletteEntries(defaults.dense().png())).isBetween(2, 64);
        Set<Integer> used = new HashSet<>();
        for (int y = 0; y < dense.getHeight(); y += 3) {
            for (int x = 0; x < dense.getWidth(); x += 3) {
                used.add(dense.getRGB(x, y) & 0xffffff);
            }
        }
        assertThat(used.size()).isLessThanOrEqualTo(64);
        assertThat(defaults.dense().version()).hasSize(16).matches("[0-9a-f]{16}");
        assertThat(defaults.dense().version()).isNotEqualTo(defaults.standard().version());
    }

    @Test
    @DisplayName("결정성: 같은 원본·같은 설정이면 바이트 단위로 같은 결과·같은 버전")
    void deterministic() {
        Result again = CONVERTER.convert(painting(176, 96, 1), Settings.defaults());
        assertThat(again.dense().png()).isEqualTo(defaults.dense().png());
        assertThat(again.standard().png()).isEqualTo(defaults.standard().png());
        assertThat(again.dense().version()).isEqualTo(defaults.dense().version());
    }

    @Test
    @DisplayName("원본이나 설정(색 수·선명도)이 바뀌면 버전이 바뀐다 · 선명도 0 은 언샤프 없이")
    void versionChanges() {
        assertThat(CONVERTER.convert(painting(176, 96, 2), Settings.defaults()).dense().version())
                .isNotEqualTo(defaults.dense().version());
        Result fewer = CONVERTER.convert(source, new Settings(16, 100));
        assertThat(fewer.dense().version()).isNotEqualTo(defaults.dense().version());
        Result flat = CONVERTER.convert(source, new Settings(64, 0));
        assertThat(flat.standard().version()).isNotEqualTo(defaults.standard().version());
    }

    @Test
    @DisplayName("큰 원본은 기준 높이 768 로 줄여 평탄화한다 — 결과 크기는 같다")
    void largeSource() {
        Result large = CONVERTER.convert(painting(1408, 800, 3), new Settings(8, 100));
        assertThat(large.dense().height()).isEqualTo(576);
        assertThat(large.dense().width())
                .isEqualTo(TowerPixelArtConverter.targetWidth(1408, 800, 576));
    }

    @Test
    @DisplayName("색이 팔레트 수보다 적으면 그 수만큼만 쓴다 (단색 원본)")
    void fewColors() throws IOException {
        BufferedImage solid = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                solid.setRGB(x, y, 0x3366cc);
            }
        }
        Result result = CONVERTER.convert(solid, Settings.defaults());
        BufferedImage image = read(result.standard().png());
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 7) {
            for (int x = 0; x < image.getWidth(); x += 7) {
                colors.add(image.getRGB(x, y) & 0xffffff);
            }
        }
        assertThat(colors).hasSize(1);
    }

    @Test
    @DisplayName("설정 범위: 색 수 2~256, 선명도 0~200 · 빈 원본 거부")
    void settingsRange() {
        assertThatThrownBy(() -> new Settings(1, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Settings(257, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Settings(64, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Settings(64, 201))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Settings.defaults()).isEqualTo(new Settings(64, 100));
    }

    @Test
    @DisplayName("외톨이 정리: 주변 8칸에 같은 색이 없으면 이웃에 가장 많은 색으로 (동률이면 작은 인덱스), 같은 색 이웃이 있으면 그대로")
    void cleanIsolated() {
        byte[] grid = {
            1, 1, 1,
            1, 5, 1,
            2, 2, 2
        };
        byte[] cleaned = TowerPixelArtConverter.cleanIsolated(grid, 3, 3);
        assertThat(cleaned[4]).isEqualTo((byte) 1);
        // 모서리 2 는 옆 칸에 같은 색 2 가 있어 그대로
        assertThat(cleaned[6]).isEqualTo((byte) 2);
        byte[] tie = {3, 4, 9};
        assertThat(TowerPixelArtConverter.cleanIsolated(tie, 3, 1)[1]).isEqualTo((byte) 3);
        assertThat(TowerPixelArtConverter.cleanIsolated(new byte[] {7}, 1, 1)[0])
                .isEqualTo((byte) 7);
    }

    @Test
    @DisplayName("Lab 변환은 되돌리면 원래 색 근처 · 면적 평균 축소는 평균색")
    void colorMath() {
        float[] lab = TowerPixelArtConverter.toLab(200, 120, 40);
        int[] back = TowerPixelArtConverter.fromLab(lab[0], lab[1], lab[2]);
        assertThat(back[0]).isBetween(199, 201);
        assertThat(back[1]).isBetween(119, 121);
        assertThat(back[2]).isBetween(39, 41);
        assertThat(TowerPixelArtConverter.fromLab(0, 0, 0)).containsExactly(0, 0, 0);
        float[] twoByOne = {0, 0, 0, 100, 50, 200};
        float[] avg = TowerPixelArtConverter.areaResize(twoByOne, 2, 1, 1, 1);
        assertThat(avg).containsExactly(50f, 25f, 100f);
        assertThat(TowerPixelArtConverter.areaResize(twoByOne, 2, 1, 2, 1))
                .containsExactly(twoByOne);
        float[] px = {10, 20, 30};
        assertThat(TowerPixelArtConverter.unsharp(px, 1, 1, 0)).isSameAs(px);
    }
}
