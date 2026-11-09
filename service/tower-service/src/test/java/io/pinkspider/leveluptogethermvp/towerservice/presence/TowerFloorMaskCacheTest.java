package io.pinkspider.leveluptogethermvp.towerservice.presence;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.towerservice.application.ImageFloorMaskChecker;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class TowerFloorMaskCacheTest {

    @TempDir Path dir;

    private TowerFloorMaskCache cache;
    private TowerFloor floor;

    @BeforeEach
    void setUp() throws IOException {
        BufferedImage mask = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        for (int x = 2; x <= 5; x++) {
            for (int y = 2; y <= 5; y++) {
                mask.setRGB(x, y, new Color(255, 0, 0, 255).getRGB());
            }
        }
        File maskFile = dir.resolve("mask.png").toFile();
        ImageIO.write(mask, "png", maskFile);
        File bgFile = dir.resolve("bg.png").toFile();
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", bgFile);

        cache = new TowerFloorMaskCache(new ImageFloorMaskChecker(List.of(), true), 600);
        floor = floor(1L, 1, null, true, true);
        ReflectionTestUtils.setField(floor, "floorMaskUrl", maskFile.toURI().toString());
        ReflectionTestUtils.setField(floor, "backgroundImageUrl", bgFile.toURI().toString());
    }

    @Test
    @DisplayName("마스크·배경을 읽어 격자로 올리고 캐시한다 · evict 뒤 다시 읽는다 · 못 읽으면 empty")
    void loadAndCache() {
        Optional<WalkableMask> first = cache.get(floor);
        assertThat(first).isPresent();
        assertThat(first.get().backgroundWidth()).isEqualTo(20);
        assertThat(first.get().isWalkable(6, 6)).isTrue();
        assertThat(cache.get(floor).get()).isSameAs(first.get());

        cache.evict(floor);
        assertThat(cache.get(floor).get()).isNotSameAs(first.get());

        TowerFloor broken = floor(2L, 2, null, true, true);
        ReflectionTestUtils.setField(
                broken, "floorMaskUrl", dir.resolve("none.png").toUri().toString());
        assertThat(cache.get(broken)).isEmpty();

        cache.evictAll();
        assertThat(cache.get(floor)).isPresent();
    }
}
