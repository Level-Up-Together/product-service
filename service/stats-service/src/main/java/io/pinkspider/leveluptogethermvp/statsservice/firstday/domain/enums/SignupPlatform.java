package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums;

import java.util.Locale;

/**
 * LUT-779: 가입 플랫폼 — {@code users.signup_device_type}(LUT-599)에서 나눈다. iPad 는 iOS. LUT-599 전 가입자는 기기를
 * 몰라 UNKNOWN(플랫폼 필터 "전체" 에만 들어간다).
 */
public enum SignupPlatform {
    IOS,
    ANDROID,
    WEB,
    UNKNOWN;

    public static SignupPlatform fromDeviceType(String deviceType) {
        if (deviceType == null) {
            return UNKNOWN;
        }
        return switch (deviceType.toLowerCase(Locale.ROOT)) {
            case "ios", "ipad" -> IOS;
            case "android" -> ANDROID;
            case "web" -> WEB;
            default -> UNKNOWN;
        };
    }
}
