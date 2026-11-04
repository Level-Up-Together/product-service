package io.pinkspider.leveluptogethermvp.towerservice.application.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * LUT-727: 층 배경(원본) → 픽셀아트 배경. 시안과 같은 순서 — ① 붓 터치 노이즈 평탄화(양방향 필터 2회 + mean-shift) ② 목표 해상도로 면적 평균
 * 축소 ③ 채도 ×1.12·언샤프 ④ Lab k-means 팔레트(기본 64색) ⑤ 주변 8칸에 같은 색이 없는 외톨이 픽셀을 최다 이웃색으로 ⑥ 인덱스 PNG.
 *
 * <p>평탄화 강도는 시안 원본(1376×768) 기준값이다 — 원본을 먼저 높이 {@link #WORK_HEIGHT} 로 면적 평균 축소해 크기와 무관하게 같은 강도로
 * 맞춘다(원본이 그보다 작으면 비율만큼 약하게).
 *
 * <p><b>결정성</b>: 같은 원본·같은 설정이면 바이트 단위로 같은 PNG 가 나와야 한다(프론트 효과 맵이 픽셀 위치 기준). 그래서 단일 스레드·고정 순서로 계산하고,
 * 초월 함수는 플랫폼마다 결과가 같은 {@link StrictMath}, k-means 시드는 고정({@link #SEED}), 팔레트는 색 순서로 정렬한다. 버전 = PNG
 * 바이트의 SHA-256 앞 16자리.
 */
public final class TowerPixelArtConverter {

    /** 고밀도 픽셀(입장 기본) 높이 */
    public static final int DENSE_HEIGHT = 576;

    /** 픽셀 높이 */
    public static final int STANDARD_HEIGHT = 384;

    /** 평탄화 기준 높이 — 시안 원본 1376×768 */
    public static final int WORK_HEIGHT = 768;

    public static final int DEFAULT_COLORS = 64;
    public static final int DEFAULT_SHARPNESS = 100;

    static final long SEED = 0x7A_C0DE_1727L;
    static final float SATURATION = 1.12f;
    static final double BILATERAL_SIGMA_COLOR = 30;
    static final double MEAN_SHIFT_COLOR_RADIUS = 20;
    static final int KMEANS_SAMPLES = 20_000;
    static final int KMEANS_ITERATIONS = 15;

    /** 설정 — 색 수 2~256, 선명도 0~200(%, 100 = 시안 값) */
    public record Settings(int colors, int sharpness) {
        public Settings {
            if (colors < 2 || colors > 256) {
                throw new IllegalArgumentException("colors must be 2..256: " + colors);
            }
            if (sharpness < 0 || sharpness > 200) {
                throw new IllegalArgumentException("sharpness must be 0..200: " + sharpness);
            }
        }

        public static Settings defaults() {
            return new Settings(DEFAULT_COLORS, DEFAULT_SHARPNESS);
        }
    }

    /** 결과 한 장 — 인덱스 PNG 바이트·크기·버전(내용 해시) */
    public record Output(byte[] png, int width, int height, String version) {}

    /** 두 가지 결과 */
    public record Result(Output dense, Output standard) {}

    /** 높이 고정, 가로는 원본 비율 (반올림, 최소 1) */
    public static int targetWidth(int sourceWidth, int sourceHeight, int targetHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("empty source");
        }
        return Math.max(1, (int) Math.round(sourceWidth * (double) targetHeight / sourceHeight));
    }

    public Result convert(BufferedImage source, Settings settings) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        if (sw <= 0 || sh <= 0) {
            throw new IllegalArgumentException("empty source");
        }
        // ① 평탄화 — 기준 높이로 맞춘 뒤 한 번만 (두 결과가 같은 평탄화 이미지를 쓴다)
        int workHeight = Math.min(sh, WORK_HEIGHT);
        int workWidth = targetWidth(sw, sh, workHeight);
        float[] work = areaResize(toRgb(source), sw, sh, workWidth, workHeight);
        double strength = workHeight / (double) WORK_HEIGHT;
        int bilateralRadius = Math.max(1, (int) Math.round(4 * strength));
        double bilateralSigmaSpace = Math.max(0.8, 3 * strength);
        work = bilateral(work, workWidth, workHeight, bilateralRadius, bilateralSigmaSpace);
        work = bilateral(work, workWidth, workHeight, bilateralRadius, bilateralSigmaSpace);
        work =
                meanShift(
                        work,
                        workWidth,
                        workHeight,
                        Math.max(1, (int) Math.round(6 * strength)),
                        MEAN_SHIFT_COLOR_RADIUS);
        return new Result(
                render(work, workWidth, workHeight, sw, sh, DENSE_HEIGHT, settings),
                render(work, workWidth, workHeight, sw, sh, STANDARD_HEIGHT, settings));
    }

    private Output render(
            float[] work,
            int workWidth,
            int workHeight,
            int sourceWidth,
            int sourceHeight,
            int targetHeight,
            Settings settings) {
        int tw = targetWidth(sourceWidth, sourceHeight, targetHeight);
        // ② 면적 평균 축소 ③ 채도·선명도
        float[] small = areaResize(work, workWidth, workHeight, tw, targetHeight);
        saturate(small, SATURATION);
        small = unsharp(small, tw, targetHeight, 0.5f * settings.sharpness() / 100f);
        // ④ 팔레트 ⑤ 외톨이 정리 ⑥ PNG
        Palette palette = quantize(small, tw * targetHeight, settings.colors());
        byte[] indices = cleanIsolated(palette.indices(), tw, targetHeight);
        byte[] png = encodePng(indices, tw, targetHeight, palette.rgb());
        return new Output(png, tw, targetHeight, version(png));
    }

    // ── 픽셀 배열 (RGB interleaved, 0~255 float) ─────────────────────────────

    static float[] toRgb(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        float[] out = new float[w * h * 3];
        for (int i = 0; i < argb.length; i++) {
            out[i * 3] = (argb[i] >> 16) & 0xff;
            out[i * 3 + 1] = (argb[i] >> 8) & 0xff;
            out[i * 3 + 2] = argb[i] & 0xff;
        }
        return out;
    }

    /** 면적 평균 리샘플 — 가로 → 세로 두 번. 축소는 겹친 면적 가중 평균, 확대는 상자 확대 */
    static float[] areaResize(float[] src, int sw, int sh, int dw, int dh) {
        if (sw == dw && sh == dh) {
            return src.clone();
        }
        float[] horizontal = new float[dw * sh * 3];
        resample1d(src, sw, sh, dw, horizontal, true);
        float[] out = new float[dw * dh * 3];
        resample1d(horizontal, dw, sh, dh, out, false);
        return out;
    }

    private static void resample1d(
            float[] src, int width, int height, int target, float[] dst, boolean horizontalPass) {
        int srcLength = horizontalPass ? width : height;
        int lines = horizontalPass ? height : width;
        double scale = srcLength / (double) target;
        int outWidth = horizontalPass ? target : width;
        for (int t = 0; t < target; t++) {
            double start = t * scale;
            double end = start + scale;
            int first = (int) Math.floor(start);
            int last = Math.min(srcLength - 1, (int) Math.ceil(end) - 1);
            for (int line = 0; line < lines; line++) {
                double r = 0;
                double g = 0;
                double b = 0;
                double total = 0;
                for (int s = first; s <= last; s++) {
                    double weight = Math.min(end, s + 1) - Math.max(start, s);
                    if (weight <= 0) {
                        continue;
                    }
                    int idx = horizontalPass ? (line * width + s) * 3 : (s * width + line) * 3;
                    r += src[idx] * weight;
                    g += src[idx + 1] * weight;
                    b += src[idx + 2] * weight;
                    total += weight;
                }
                int out = horizontalPass ? (line * outWidth + t) * 3 : (t * outWidth + line) * 3;
                dst[out] = (float) (r / total);
                dst[out + 1] = (float) (g / total);
                dst[out + 2] = (float) (b / total);
            }
        }
    }

    /** 양방향 필터 — 가장자리는 지키고 붓 터치 노이즈만 편다. 가중치는 표로 미리 계산(StrictMath) */
    static float[] bilateral(float[] src, int w, int h, int radius, double sigmaSpace) {
        int size = radius * 2 + 1;
        float[] spatial = new float[size * size];
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                spatial[(dy + radius) * size + dx + radius] =
                        (float)
                                StrictMath.exp(
                                        -(dx * dx + dy * dy) / (2 * sigmaSpace * sigmaSpace));
            }
        }
        float[] colorWeight = new float[3 * 255 * 255 + 1];
        for (int d = 0; d < colorWeight.length; d++) {
            colorWeight[d] =
                    (float)
                            StrictMath.exp(
                                    -d / (2 * BILATERAL_SIGMA_COLOR * BILATERAL_SIGMA_COLOR));
        }
        float[] out = new float[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int center = (y * w + x) * 3;
                float cr = src[center];
                float cg = src[center + 1];
                float cb = src[center + 2];
                float sr = 0;
                float sg = 0;
                float sb = 0;
                float total = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    int yy = Math.min(h - 1, Math.max(0, y + dy));
                    for (int dx = -radius; dx <= radius; dx++) {
                        int xx = Math.min(w - 1, Math.max(0, x + dx));
                        int idx = (yy * w + xx) * 3;
                        float r = src[idx];
                        float g = src[idx + 1];
                        float b = src[idx + 2];
                        int dist =
                                Math.round(
                                        (r - cr) * (r - cr)
                                                + (g - cg) * (g - cg)
                                                + (b - cb) * (b - cb));
                        float weight =
                                spatial[(dy + radius) * size + dx + radius]
                                        * colorWeight[Math.min(dist, colorWeight.length - 1)];
                        sr += r * weight;
                        sg += g * weight;
                        sb += b * weight;
                        total += weight;
                    }
                }
                out[center] = sr / total;
                out[center + 1] = sg / total;
                out[center + 2] = sb / total;
            }
        }
        return out;
    }

    /** mean-shift 필터 — 공간 반경 안·색 반경 안 이웃의 평균으로 수렴할 때까지(최대 5번) 옮겨 그 색을 쓴다(넓은 면을 평평하게) */
    static float[] meanShift(float[] src, int w, int h, int spatialRadius, double colorRadius) {
        float colorLimit = (float) (colorRadius * colorRadius);
        float[] out = new float[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int start = (y * w + x) * 3;
                float px = x;
                float py = y;
                float cr = src[start];
                float cg = src[start + 1];
                float cb = src[start + 2];
                for (int iter = 0; iter < 5; iter++) {
                    int ix = Math.round(px);
                    int iy = Math.round(py);
                    float sx = 0;
                    float sy = 0;
                    float sr = 0;
                    float sg = 0;
                    float sb = 0;
                    int count = 0;
                    for (int yy = Math.max(0, iy - spatialRadius);
                            yy <= Math.min(h - 1, iy + spatialRadius);
                            yy++) {
                        for (int xx = Math.max(0, ix - spatialRadius);
                                xx <= Math.min(w - 1, ix + spatialRadius);
                                xx++) {
                            int idx = (yy * w + xx) * 3;
                            float dr = src[idx] - cr;
                            float dg = src[idx + 1] - cg;
                            float db = src[idx + 2] - cb;
                            if (dr * dr + dg * dg + db * db > colorLimit) {
                                continue;
                            }
                            sx += xx;
                            sy += yy;
                            sr += src[idx];
                            sg += src[idx + 1];
                            sb += src[idx + 2];
                            count++;
                        }
                    }
                    if (count == 0) {
                        break;
                    }
                    float nx = sx / count;
                    float ny = sy / count;
                    float nr = sr / count;
                    float ng = sg / count;
                    float nb = sb / count;
                    float shift =
                            Math.abs(nx - px)
                                    + Math.abs(ny - py)
                                    + Math.abs(nr - cr)
                                    + Math.abs(ng - cg)
                                    + Math.abs(nb - cb);
                    px = nx;
                    py = ny;
                    cr = nr;
                    cg = ng;
                    cb = nb;
                    if (shift < 1f) {
                        break;
                    }
                }
                out[start] = cr;
                out[start + 1] = cg;
                out[start + 2] = cb;
            }
        }
        return out;
    }

    /** 채도 — 밝기(Rec.601)는 그대로 두고 색 차이만 늘린다 */
    static void saturate(float[] px, float factor) {
        for (int i = 0; i < px.length; i += 3) {
            float luma = 0.299f * px[i] + 0.587f * px[i + 1] + 0.114f * px[i + 2];
            for (int c = 0; c < 3; c++) {
                px[i + c] = clamp(luma + (px[i + c] - luma) * factor);
            }
        }
    }

    /** 언샤프 — 가우시안(σ=1) 흐림과의 차이를 amount 만큼 더한다. amount 0 이면 그대로 */
    static float[] unsharp(float[] px, int w, int h, float amount) {
        if (amount <= 0) {
            return px;
        }
        float[] kernel = new float[5];
        float sum = 0;
        for (int i = -2; i <= 2; i++) {
            kernel[i + 2] = (float) StrictMath.exp(-(i * i) / 2.0);
            sum += kernel[i + 2];
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        float[] tmp = new float[px.length];
        float[] blur = new float[px.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                for (int c = 0; c < 3; c++) {
                    float acc = 0;
                    for (int k = -2; k <= 2; k++) {
                        int xx = Math.min(w - 1, Math.max(0, x + k));
                        acc += px[(y * w + xx) * 3 + c] * kernel[k + 2];
                    }
                    tmp[(y * w + x) * 3 + c] = acc;
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                for (int c = 0; c < 3; c++) {
                    float acc = 0;
                    for (int k = -2; k <= 2; k++) {
                        int yy = Math.min(h - 1, Math.max(0, y + k));
                        acc += tmp[(yy * w + x) * 3 + c] * kernel[k + 2];
                    }
                    blur[(y * w + x) * 3 + c] = acc;
                }
            }
        }
        float[] out = new float[px.length];
        for (int i = 0; i < px.length; i++) {
            out[i] = clamp(px[i] + amount * (px[i] - blur[i]));
        }
        return out;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    // ── 팔레트 (Lab k-means) ─────────────────────────────

    record Palette(byte[] indices, int[] rgb) {}

    /** Lab 에서 k-means(고정 시드 k-means++) → 팔레트는 소속 픽셀 평균색, 밝기·색 순서로 정렬해 인덱스를 고정한다 */
    static Palette quantize(float[] px, int count, int colors) {
        float[] lab = new float[count * 3];
        for (int i = 0; i < count; i++) {
            float[] l = toLab(px[i * 3], px[i * 3 + 1], px[i * 3 + 2]);
            lab[i * 3] = l[0];
            lab[i * 3 + 1] = l[1];
            lab[i * 3 + 2] = l[2];
        }
        int stride = Math.max(1, count / KMEANS_SAMPLES);
        int samples = (count + stride - 1) / stride;
        float[] sample = new float[samples * 3];
        for (int s = 0; s < samples; s++) {
            System.arraycopy(lab, s * stride * 3, sample, s * 3, 3);
        }
        int k = Math.min(colors, countDistinct(sample, samples));
        float[] centers = initCenters(sample, samples, k, new Random(SEED));
        int[] assign = new int[samples];
        for (int iter = 0; iter < KMEANS_ITERATIONS; iter++) {
            boolean changed = false;
            for (int s = 0; s < samples; s++) {
                int nearest = nearest(centers, k, sample, s);
                if (nearest != assign[s]) {
                    changed = true;
                    assign[s] = nearest;
                }
            }
            double[] sums = new double[k * 3];
            int[] counts = new int[k];
            for (int s = 0; s < samples; s++) {
                int c = assign[s];
                sums[c * 3] += sample[s * 3];
                sums[c * 3 + 1] += sample[s * 3 + 1];
                sums[c * 3 + 2] += sample[s * 3 + 2];
                counts[c]++;
            }
            for (int c = 0; c < k; c++) {
                if (counts[c] == 0) {
                    continue;
                }
                centers[c * 3] = (float) (sums[c * 3] / counts[c]);
                centers[c * 3 + 1] = (float) (sums[c * 3 + 1] / counts[c]);
                centers[c * 3 + 2] = (float) (sums[c * 3 + 2] / counts[c]);
            }
            if (!changed && iter > 0) {
                break;
            }
        }
        // 전체 픽셀 배정 + 팔레트 = 소속 픽셀의 평균 RGB
        int[] cluster = new int[count];
        double[] rgbSums = new double[k * 3];
        int[] members = new int[k];
        for (int i = 0; i < count; i++) {
            int c = nearest(centers, k, lab, i);
            cluster[i] = c;
            rgbSums[c * 3] += px[i * 3];
            rgbSums[c * 3 + 1] += px[i * 3 + 1];
            rgbSums[c * 3 + 2] += px[i * 3 + 2];
            members[c]++;
        }
        Integer[] order = new Integer[k];
        int[] rgb = new int[k];
        float[] sortKey = new float[k];
        for (int c = 0; c < k; c++) {
            order[c] = c;
            int r;
            int g;
            int b;
            if (members[c] > 0) {
                r = Math.round((float) (rgbSums[c * 3] / members[c]));
                g = Math.round((float) (rgbSums[c * 3 + 1] / members[c]));
                b = Math.round((float) (rgbSums[c * 3 + 2] / members[c]));
            } else {
                int[] back = fromLab(centers[c * 3], centers[c * 3 + 1], centers[c * 3 + 2]);
                r = back[0];
                g = back[1];
                b = back[2];
            }
            rgb[c] = (r << 16) | (g << 8) | b;
            sortKey[c] = 0.299f * r + 0.587f * g + 0.114f * b;
        }
        Arrays.sort(
                order,
                (a, b) -> {
                    int byLuma = Float.compare(sortKey[a], sortKey[b]);
                    return byLuma != 0 ? byLuma : Integer.compare(rgb[a], rgb[b]);
                });
        int[] remap = new int[k];
        int[] sortedRgb = new int[k];
        for (int pos = 0; pos < k; pos++) {
            remap[order[pos]] = pos;
            sortedRgb[pos] = rgb[order[pos]];
        }
        byte[] indices = new byte[count];
        for (int i = 0; i < count; i++) {
            indices[i] = (byte) remap[cluster[i]];
        }
        return new Palette(indices, sortedRgb);
    }

    private static int countDistinct(float[] sample, int samples) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int s = 0; s < samples && seen.size() < 257; s++) {
            long key =
                    ((long) Float.floatToIntBits(sample[s * 3]) << 40)
                            ^ ((long) Float.floatToIntBits(sample[s * 3 + 1]) << 20)
                            ^ Float.floatToIntBits(sample[s * 3 + 2]);
            seen.add(key);
        }
        return Math.max(1, seen.size());
    }

    /** k-means++ — 첫 중심은 시드 난수, 이후는 거리² 비례 확률 */
    private static float[] initCenters(float[] sample, int samples, int k, Random random) {
        float[] centers = new float[k * 3];
        int first = random.nextInt(samples);
        System.arraycopy(sample, first * 3, centers, 0, 3);
        double[] dist = new double[samples];
        for (int c = 1; c < k; c++) {
            double total = 0;
            for (int s = 0; s < samples; s++) {
                double best = Double.MAX_VALUE;
                for (int j = 0; j < c; j++) {
                    best = Math.min(best, distance(centers, j, sample, s));
                }
                dist[s] = best;
                total += best;
            }
            int chosen = samples - 1;
            if (total > 0) {
                double target = random.nextDouble() * total;
                double acc = 0;
                for (int s = 0; s < samples; s++) {
                    acc += dist[s];
                    if (acc >= target) {
                        chosen = s;
                        break;
                    }
                }
            } else {
                chosen = random.nextInt(samples);
            }
            System.arraycopy(sample, chosen * 3, centers, c * 3, 3);
        }
        return centers;
    }

    private static int nearest(float[] centers, int k, float[] points, int p) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int c = 0; c < k; c++) {
            double d = distance(centers, c, points, p);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    private static double distance(float[] a, int ai, float[] b, int bi) {
        double dl = a[ai * 3] - b[bi * 3];
        double da = a[ai * 3 + 1] - b[bi * 3 + 1];
        double db = a[ai * 3 + 2] - b[bi * 3 + 2];
        return dl * dl + da * da + db * db;
    }

    /** sRGB(0~255) → CIE Lab (D65) */
    static float[] toLab(float r, float g, float b) {
        double lr = linear(r / 255.0);
        double lg = linear(g / 255.0);
        double lb = linear(b / 255.0);
        double x = (0.4124564 * lr + 0.3575761 * lg + 0.1804375 * lb) / 0.95047;
        double y = 0.2126729 * lr + 0.7151522 * lg + 0.0721750 * lb;
        double z = (0.0193339 * lr + 0.1191920 * lg + 0.9503041 * lb) / 1.08883;
        double fx = labF(x);
        double fy = labF(y);
        double fz = labF(z);
        return new float[] {
            (float) (116 * fy - 16), (float) (500 * (fx - fy)), (float) (200 * (fy - fz))
        };
    }

    /** CIE Lab → sRGB(0~255) — 빈 군집의 중심색에만 쓴다 */
    static int[] fromLab(float l, float a, float bb) {
        double fy = (l + 16) / 116.0;
        double fx = fy + a / 500.0;
        double fz = fy - bb / 200.0;
        double x = labFInverse(fx) * 0.95047;
        double y = labFInverse(fy);
        double z = labFInverse(fz) * 1.08883;
        double lr = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z;
        double lg = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z;
        double lb = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z;
        return new int[] {toByte(gamma(lr)), toByte(gamma(lg)), toByte(gamma(lb))};
    }

    private static double linear(double c) {
        return c <= 0.04045 ? c / 12.92 : StrictMath.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double gamma(double c) {
        return c <= 0.0031308 ? 12.92 * c : 1.055 * StrictMath.pow(c, 1 / 2.4) - 0.055;
    }

    private static double labF(double t) {
        return t > 216.0 / 24389 ? StrictMath.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    private static double labFInverse(double t) {
        double cube = t * t * t;
        return cube > 216.0 / 24389 ? cube : (116 * t - 16) / (24389.0 / 27);
    }

    private static int toByte(double c) {
        return (int) Math.max(0, Math.min(255, Math.round(c * 255)));
    }

    // ── 외톨이 정리·PNG·버전 ─────────────────────────────

    /** 주변 8칸에 같은 색이 하나도 없는 픽셀을 이웃에 가장 많은 색으로 (동률이면 작은 인덱스). 원래 인덱스 기준 한 번 */
    static byte[] cleanIsolated(byte[] indices, int w, int h) {
        byte[] out = indices.clone();
        int[] counts = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int self = indices[y * w + x] & 0xff;
                boolean hasSame = false;
                int neighbors = 0;
                Arrays.fill(counts, 0);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        int xx = x + dx;
                        int yy = y + dy;
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) {
                            continue;
                        }
                        int v = indices[yy * w + xx] & 0xff;
                        if (v == self) {
                            hasSame = true;
                        }
                        counts[v]++;
                        neighbors++;
                    }
                }
                if (hasSame || neighbors == 0) {
                    continue;
                }
                int best = 0;
                for (int v = 1; v < 256; v++) {
                    if (counts[v] > counts[best]) {
                        best = v;
                    }
                }
                out[y * w + x] = (byte) best;
            }
        }
        return out;
    }

    /** 8비트 인덱스 PNG — 실제 해상도 그대로(프론트가 보간 없이 확대한다) */
    static byte[] encodePng(byte[] indices, int w, int h, int[] palette) {
        byte[] r = new byte[palette.length];
        byte[] g = new byte[palette.length];
        byte[] b = new byte[palette.length];
        for (int i = 0; i < palette.length; i++) {
            r[i] = (byte) (palette[i] >> 16);
            g[i] = (byte) (palette[i] >> 8);
            b[i] = (byte) palette[i];
        }
        IndexColorModel model = new IndexColorModel(8, palette.length, r, g, b);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, model);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(indices, 0, data, 0, indices.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "png", out)) {
                throw new IOException("no png writer");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** 내용 해시 — SHA-256 앞 16자리 */
    static String version(byte[] png) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(png);
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
