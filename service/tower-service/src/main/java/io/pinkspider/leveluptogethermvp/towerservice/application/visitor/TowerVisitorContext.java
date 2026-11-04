package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import org.springframework.lang.Nullable;

/**
 * LUT-705: 방문객 요청이 가진 신원 단서 — 클라이언트가 보낸 방문객 키(세션)·기기 ID 와 서버가 본 IP·User-Agent. 로그인 유저 요청에는 쓰지 않는다.
 * 내부 호출(테스트·서버 안)은 {@link #none()}.
 */
public record TowerVisitorContext(
        @Nullable String visitorKey,
        @Nullable String deviceId,
        @Nullable String ip,
        @Nullable String userAgent) {

    private static final int MAX_DEVICE_ID = 128;
    private static final int MAX_USER_AGENT = 500;

    public static TowerVisitorContext none() {
        return new TowerVisitorContext(null, null, null, null);
    }

    public static TowerVisitorContext of(
            @Nullable String visitorKey,
            @Nullable String deviceId,
            @Nullable String ip,
            @Nullable String userAgent) {
        return new TowerVisitorContext(
                blankToNull(visitorKey, 64),
                blankToNull(deviceId, MAX_DEVICE_ID),
                blankToNull(ip, 64),
                blankToNull(userAgent, MAX_USER_AGENT));
    }

    private static String blankToNull(@Nullable String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
