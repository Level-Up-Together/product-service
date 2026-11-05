package io.pinkspider.leveluptogethermvp.towerservice.application;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LUT-629: 마스크 PNG 를 읽어 픽셀 색으로 판정한다. 붉은색(R 높고 G·B 낮음, 투명 아님)이 이동 가능 영역이다.
 *
 * <p>마스크 해상도가 배경보다 낮으면(4분의 1 권장) 배경 크기 대비 비율로 좌표를 옮긴다. 배경을 못 읽으면 마스크와 같은 해상도로 본다.
 *
 * <p><b>SSRF 방어</b>: 어드민이 넣은 URL 을 서버가 가져오므로 {@code https} 만, 설정 {@code
 * tower.floor.allowed-image-hosts}(CDN 호스트) 에 있는 호스트만, 그 호스트가 루프백·사설·링크로컬 주소로 풀리지 않을 때만 읽는다. 리다이렉트는
 * 따라가지 않는다(3xx = 읽기 실패). 그 밖의 URL 은 {@link IOException} → 저장 거부 {@code 150109}.
 */
@Component
public class ImageFloorMaskChecker implements FloorMaskChecker {

    static final int RED_MIN = 160;
    static final int OTHER_MAX = 100;
    private static final int TIMEOUT_MS = 5_000;

    private final Set<String> allowedHosts;
    private final boolean allowLocalFiles;

    @Autowired
    public ImageFloorMaskChecker(
            @Value("${tower.floor.allowed-image-hosts:}") List<String> allowedHosts) {
        this(allowedHosts, false);
    }

    /** 테스트 전용: {@code file:} URL 허용 */
    public ImageFloorMaskChecker(List<String> allowedHosts, boolean allowLocalFiles) {
        this.allowedHosts =
                allowedHosts == null
                        ? Set.of()
                        : allowedHosts.stream()
                                .map(ImageFloorMaskChecker::normalizeHost)
                                .filter(h -> h != null && !h.isEmpty())
                                .collect(Collectors.toUnmodifiableSet());
        this.allowLocalFiles = allowLocalFiles;
    }

    @Override
    public boolean isWalkable(String maskUrl, String backgroundUrl, int x, int y)
            throws IOException {
        BufferedImage mask = load(maskUrl);
        if (mask == null) {
            throw new IOException("mask image unreadable: " + maskUrl);
        }
        int bgWidth = mask.getWidth();
        int bgHeight = mask.getHeight();
        if (backgroundUrl != null && !backgroundUrl.isBlank()) {
            try {
                BufferedImage background = load(backgroundUrl);
                if (background != null) {
                    bgWidth = background.getWidth();
                    bgHeight = background.getHeight();
                }
            } catch (IOException ignored) {
                // 배경을 못 읽으면 마스크 좌표계로 판정 (마스크가 전체 해상도일 때와 같다)
            }
        }
        if (x < 0 || y < 0 || x >= bgWidth || y >= bgHeight) {
            return false;
        }
        int mx = (int) Math.floor(x * (mask.getWidth() / (double) bgWidth));
        int my = (int) Math.floor(y * (mask.getHeight() / (double) bgHeight));
        mx = Math.min(mx, mask.getWidth() - 1);
        my = Math.min(my, mask.getHeight() - 1);
        return isRed(mask.getRGB(mx, my));
    }

    @Override
    public boolean isInsideImage(String imageUrl, int x, int y) throws IOException {
        BufferedImage image = load(imageUrl);
        if (image == null) {
            throw new IOException("image unreadable: " + imageUrl);
        }
        return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight();
    }

    /** 비율 허용 오차 — 4분의 1 축소에서 생기는 반올림(한 변 1px) 정도 */
    static final double ASPECT_TOLERANCE = 0.02;

    @Override
    public boolean sameAspectRatio(String maskUrl, String backgroundUrl) throws IOException {
        BufferedImage mask = load(maskUrl);
        BufferedImage background = load(backgroundUrl);
        if (mask == null || background == null) {
            throw new IOException("image unreadable");
        }
        double maskRatio = mask.getWidth() / (double) mask.getHeight();
        double backgroundRatio = background.getWidth() / (double) background.getHeight();
        return Math.abs(maskRatio - backgroundRatio) / backgroundRatio <= ASPECT_TOLERANCE;
    }

    public static boolean isRed(int argb) {
        int alpha = (argb >>> 24) & 0xff;
        int r = (argb >>> 16) & 0xff;
        int g = (argb >>> 8) & 0xff;
        int b = argb & 0xff;
        return alpha > 0 && r >= RED_MIN && g <= OTHER_MAX && b <= OTHER_MAX;
    }

    /** SSRF 방어를 거쳐 이미지를 읽는다 (LUT-631 마스크 캐시도 같은 경로) */
    public BufferedImage load(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid url", e);
        }
        if (allowLocalFiles && "file".equalsIgnoreCase(uri.getScheme())) {
            try (InputStream in = uri.toURL().openStream()) {
                return ImageIO.read(in);
            }
        }
        validateRemote(uri);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("image fetch failed: http " + status);
        }
        try (InputStream in = connection.getInputStream()) {
            return ImageIO.read(in);
        } finally {
            connection.disconnect();
        }
    }

    /** https + 허용 호스트 + 공인 주소만 — 어드민 입력으로 내부망을 찌르지 못하게 */
    void validateRemote(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("only https image urls are allowed");
        }
        String host = normalizeHost(uri.getHost());
        if (host == null || !allowedHosts.contains(host)) {
            throw new IOException("image host not allowed: " + host);
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IOException("image host resolves to a private address: " + host);
            }
        }
    }

    static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        return h.endsWith(".") ? h.substring(0, h.length() - 1) : h;
    }
}
