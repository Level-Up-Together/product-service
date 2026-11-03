package io.pinkspider.leveluptogethermvp.towerservice.presence;

import java.awt.image.BufferedImage;
import org.springframework.lang.Nullable;

/**
 * LUT-631: 층 바닥 마스크를 격자로 올린 것. 좌표는 배경 원본 픽셀로 받고, 마스크가 배경보다 작으면 비율로 옮겨 판정한다. {@link #snapInside} 는
 * 마스크 밖 지점을 가장 가까운 안쪽으로 보정한다(클라이언트 조작 방지).
 */
public class WalkableMask {

    public record Point(int x, int y) {}

    private final boolean[][] walkable; // [y][x] 마스크 픽셀
    private final int maskWidth;
    private final int maskHeight;
    private final int backgroundWidth;
    private final int backgroundHeight;

    public WalkableMask(BufferedImage mask, int backgroundWidth, int backgroundHeight) {
        this.maskWidth = mask.getWidth();
        this.maskHeight = mask.getHeight();
        this.backgroundWidth = backgroundWidth > 0 ? backgroundWidth : maskWidth;
        this.backgroundHeight = backgroundHeight > 0 ? backgroundHeight : maskHeight;
        this.walkable = new boolean[maskHeight][maskWidth];
        for (int y = 0; y < maskHeight; y++) {
            for (int x = 0; x < maskWidth; x++) {
                walkable[y][x] =
                        io.pinkspider.leveluptogethermvp.towerservice.application
                                .ImageFloorMaskChecker.isRed(mask.getRGB(x, y));
            }
        }
    }

    public int backgroundWidth() {
        return backgroundWidth;
    }

    public int backgroundHeight() {
        return backgroundHeight;
    }

    public boolean isWalkable(int x, int y) {
        if (x < 0 || y < 0 || x >= backgroundWidth || y >= backgroundHeight) {
            return false;
        }
        int mx = toMaskX(x);
        int my = toMaskY(y);
        return walkable[my][mx];
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
