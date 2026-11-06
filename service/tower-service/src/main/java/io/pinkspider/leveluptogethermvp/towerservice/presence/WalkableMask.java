package io.pinkspider.leveluptogethermvp.towerservice.presence;

import java.awt.image.BufferedImage;
import java.util.Random;
import org.springframework.lang.Nullable;

/**
 * LUT-631: 층 바닥 마스크를 격자로 올린 것. 좌표는 배경 원본 픽셀로 받고, 마스크가 배경보다 작으면 비율로 옮겨 판정한다. {@link #snapInside} 는
 * 마스크 밖 지점을 가장 가까운 안쪽으로 보정한다(클라이언트 조작 방지). {@link #randomWalkable} 은 바닥 안 아무 칸을 고른다(LUT-757 자동 착석
 * 자리).
 */
public class WalkableMask {

    public record Point(int x, int y) {}

    /** 랜덤 칸 고르기 — 무작위로 찍어 보는 횟수. 다 빗나가면 k 번째 바닥 칸을 훑어서 찾는다(바닥이 아주 좁은 마스크) */
    static final int RANDOM_TRIES = 64;

    private final boolean[][] walkable; // [y][x] 마스크 픽셀
    private final int maskWidth;
    private final int maskHeight;
    private final int backgroundWidth;
    private final int backgroundHeight;
    private final int walkableCount;

    public WalkableMask(BufferedImage mask, int backgroundWidth, int backgroundHeight) {
        this.maskWidth = mask.getWidth();
        this.maskHeight = mask.getHeight();
        this.backgroundWidth = backgroundWidth > 0 ? backgroundWidth : maskWidth;
        this.backgroundHeight = backgroundHeight > 0 ? backgroundHeight : maskHeight;
        this.walkable = new boolean[maskHeight][maskWidth];
        int count = 0;
        for (int y = 0; y < maskHeight; y++) {
            for (int x = 0; x < maskWidth; x++) {
                boolean red =
                        io.pinkspider.leveluptogethermvp.towerservice.application
                                .ImageFloorMaskChecker.isRed(mask.getRGB(x, y));
                walkable[y][x] = red;
                if (red) {
                    count++;
                }
            }
        }
        this.walkableCount = count;
    }

    public int backgroundWidth() {
        return backgroundWidth;
    }

    public int backgroundHeight() {
        return backgroundHeight;
    }

    /** 바닥(붉은) 칸 수 — 마스크 픽셀 단위 */
    public int walkableCount() {
        return walkableCount;
    }

    public boolean isWalkable(int x, int y) {
        if (x < 0 || y < 0 || x >= backgroundWidth || y >= backgroundHeight) {
            return false;
        }
        int mx = toMaskX(x);
        int my = toMaskY(y);
        return walkable[my][mx];
    }

    /**
     * LUT-757: 바닥 안 칸 하나를 무작위로 골라 배경 좌표(칸 가운데)로 돌려준다. 바닥 칸이 하나도 없으면 null. 무작위로 찍어 {@link
     * #RANDOM_TRIES} 번 안에 바닥을 못 맞히면(바닥이 아주 좁은 마스크) k 번째 바닥 칸을 훑어서 찾는다 — 바닥이 있으면 반드시 하나를 돌려준다.
     */
    @Nullable
    public Point randomWalkable(Random random) {
        if (walkableCount == 0) {
            return null;
        }
        for (int i = 0; i < RANDOM_TRIES; i++) {
            int mx = random.nextInt(maskWidth);
            int my = random.nextInt(maskHeight);
            if (walkable[my][mx]) {
                return new Point(toBackgroundX(mx), toBackgroundY(my));
            }
        }
        int k = random.nextInt(walkableCount);
        for (int y = 0; y < maskHeight; y++) {
            for (int x = 0; x < maskWidth; x++) {
                if (walkable[y][x] && k-- == 0) {
                    return new Point(toBackgroundX(x), toBackgroundY(y));
                }
            }
        }
        return null; // walkableCount 와 격자가 어긋날 수 없으므로 오지 않는다
    }

    /** 밖이면 반지름 {@code maxRadiusMaskPx}(마스크 픽셀) 안에서 가장 가까운 안쪽 점을 배경 좌표로 돌려준다. 없으면 null */
    @Nullable
    public Point snapInside(int x, int y, int maxRadiusMaskPx) {
        int cx = Math.max(0, Math.min(backgroundWidth - 1, x));
        int cy = Math.max(0, Math.min(backgroundHeight - 1, y));
        if (isWalkable(cx, cy)) {
            return new Point(cx, cy);
        }
        int mx = toMaskX(cx);
        int my = toMaskY(cy);
        for (int r = 1; r <= maxRadiusMaskPx; r++) {
            Point best = null;
            long bestDist = Long.MAX_VALUE;
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue; // 링만
                    }
                    int px = mx + dx;
                    int py = my + dy;
                    if (px < 0
                            || py < 0
                            || px >= maskWidth
                            || py >= maskHeight
                            || !walkable[py][px]) {
                        continue;
                    }
                    long dist = (long) dx * dx + (long) dy * dy;
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = new Point(toBackgroundX(px), toBackgroundY(py));
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private int toMaskX(int x) {
        return Math.min(
                maskWidth - 1, (int) Math.floor(x * (maskWidth / (double) backgroundWidth)));
    }

    private int toMaskY(int y) {
        return Math.min(
                maskHeight - 1, (int) Math.floor(y * (maskHeight / (double) backgroundHeight)));
    }

    private int toBackgroundX(int mx) {
        // 마스크 픽셀의 가운데를 배경 좌표로
        return Math.min(
                backgroundWidth - 1,
                (int) Math.floor((mx + 0.5) * (backgroundWidth / (double) maskWidth)));
    }

    private int toBackgroundY(int my) {
        return Math.min(
                backgroundHeight - 1,
                (int) Math.floor((my + 0.5) * (backgroundHeight / (double) maskHeight)));
    }
}
