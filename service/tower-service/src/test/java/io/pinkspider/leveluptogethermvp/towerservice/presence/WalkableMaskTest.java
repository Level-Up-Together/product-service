package io.pinkspider.leveluptogethermvp.towerservice.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LUT-631: 마스크 격자 — 안/밖 판정과 가장 가까운 안쪽 보정 */
class WalkableMaskTest {

    /** 10×10 마스크, (2..5, 2..5) 만 붉은색 */
    private static BufferedImage mask() {
        BufferedImage mask = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        for (int x = 2; x <= 5; x++) {
            for (int y = 2; y <= 5; y++) {
                mask.setRGB(x, y, new Color(220, 30, 30, 255).getRGB());
            }
        }
        return mask;
    }

    @Test
    @DisplayName("배경이 마스크의 2배면 좌표를 비율로 옮겨 판정한다")
    void scaled() {
        WalkableMask m = new WalkableMask(mask(), 20, 20);
        assertThat(m.backgroundWidth()).isEqualTo(20);
        assertThat(m.backgroundHeight()).isEqualTo(20);
        assertThat(m.isWalkable(6, 6)).isTrue(); // 마스크 (3,3)
        assertThat(m.isWalkable(16, 16)).isFalse();
        assertThat(m.isWalkable(-1, 5)).isFalse();
        assertThat(m.isWalkable(20, 5)).isFalse();
    }

    @Test
    @DisplayName("밖이면 가장 가까운 안쪽 점으로 보정, 반지름 안에 없으면 null")
    void snap() {
        WalkableMask m = new WalkableMask(mask(), 0, 0); // 배경 = 마스크 해상도
        assertThat(m.snapInside(3, 3, 5)).isEqualTo(new WalkableMask.Point(3, 3));
        WalkableMask.Point snapped = m.snapInside(9, 3, 10);
        assertThat(snapped).isNotNull();
        assertThat(m.isWalkable(snapped.x(), snapped.y())).isTrue();
        assertThat(snapped.x()).isEqualTo(5); // 바로 왼쪽 가장자리
        assertThat(m.snapInside(9, 9, 2)).isNull();
        WalkableMask.Point far = m.snapInside(-50, 3, 10); // 화면 밖 좌표도 안으로 당겨서 찾는다
        assertThat(far).isNotNull();
        assertThat(m.isWalkable(far.x(), far.y())).isTrue();
    }

    @Test
    @DisplayName("LUT-757: 랜덤 칸은 늘 바닥 안 · 배경 배율이 있으면 칸 가운데의 배경 좌표 · 바닥 칸 수")
    void randomWalkable() {
        WalkableMask m = new WalkableMask(mask(), 20, 20);
        assertThat(m.walkableCount()).isEqualTo(16);
        Random random = new Random(42);
        java.util.Set<WalkableMask.Point> seen = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            WalkableMask.Point p = m.randomWalkable(random);
            assertThat(p).isNotNull();
            assertThat(m.isWalkable(p.x(), p.y())).isTrue();
            assertThat(p.x()).isBetween(5, 11); // 마스크 2..5 의 가운데(×2 + 1)
            assertThat(p.y()).isBetween(5, 11);
            seen.add(p);
        }
        assertThat(seen).hasSizeGreaterThan(1); // 한 자리만 나오지 않는다
    }

    @Test
    @DisplayName("LUT-757: 무작위로 못 맞히면 k 번째 바닥 칸을 훑어서 찾는다 · 바닥이 없으면 null")
    void randomWalkableFallback() {
        BufferedImage single = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        single.setRGB(7, 8, new Color(220, 30, 30, 255).getRGB());
        WalkableMask m = new WalkableMask(single, 0, 0);
        assertThat(m.walkableCount()).isEqualTo(1);
        Random alwaysZero =
                new Random() {
                    @Override
                    public int nextInt(int bound) {
                        return 0; // (0,0) 만 찍는다 → 바닥이 아니라 전부 빗나간다
                    }
                };
        assertThat(m.randomWalkable(alwaysZero)).isEqualTo(new WalkableMask.Point(7, 8));

        WalkableMask empty =
                new WalkableMask(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), 0, 0);
        assertThat(empty.walkableCount()).isZero();
        assertThat(empty.randomWalkable(new Random(1))).isNull();
    }
}
