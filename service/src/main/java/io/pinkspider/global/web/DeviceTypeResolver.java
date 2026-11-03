package io.pinkspider.global.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * deviceType 정규화 (LUT-336).
 *
 * <p>기존에는 해석 방식이 세 갈래였다 — 재발급은 요청 본문, 로그아웃/상태조회는 {@code X-Device-Type} 헤더(기본 web), 모바일 로그인은 값이 없으면
 * {@code "mobile"}. 그래서 같은 기기가 로그인은 {@code mobile}, 재발급은 {@code ios} 로 기록되는 등 값이 어긋났다.
 *
 * <p>deviceType 은 더 이상 세션 키의 일부가 아니므로(세션 키는 deviceId 기준) 값이 어긋나도 로그아웃으로 이어지지는 않지만, 세션 목록에 표시되는 값이라
 * 한 곳에서 같은 규칙으로 정규화한다.
 *
 * <p>클라이언트가 보낸 값이 있으면 그 값을 우선한다. User-Agent 추정으로 덮어쓰지 않는 이유는, 예를 들어 React Native 의 {@code
 * Platform.OS} 는 iPad 에서도 {@code ios} 를 돌려주는데 서버가 UA 로 {@code ipad} 라고 판정해 버리면 같은 기기의 값이 호출마다 달라지기
 * 때문이다.
 */
@Component
public class DeviceTypeResolver {

    public static final String WEB = "web";
    public static final String IOS = "ios";
    public static final String IPAD = "ipad";
    public static final String ANDROID = "android";

    private static final Set<String> KNOWN = Set.of(WEB, IOS, IPAD, ANDROID);

    /** 클라이언트가 보낸 값 우선, 없으면 User-Agent 로 추정. 최종 폴백은 web. */
    public String resolve(HttpServletRequest request, String clientProvided) {
        String normalized = normalize(clientProvided);
        if (normalized != null) {
            return normalized;
        }
        return fromUserAgent(request == null ? null : request.getHeader("User-Agent"));
    }

    /**
     * 알려진 값이면 소문자로 정규화해 반환, 아니면 null. 레거시 {@code "mobile"} 처럼 플랫폼을 특정하지 못하는 값은 미지정으로 취급해 UA 추정에
     * 맡긴다.
     */
    public String normalize(String deviceType) {
        if (deviceType == null || deviceType.isBlank()) {
            return null;
        }
        String lower = deviceType.trim().toLowerCase(Locale.ROOT);
        return KNOWN.contains(lower) ? lower : null;
    }

    /**
     * User-Agent 기반 추정. 판정 불가 시 web.
     *
     * <p>웹뷰·브라우저 UA(iphone/ipad/android)뿐 아니라 RN 네이티브 fetch 의 기본 UA 도 앱으로 판정한다(LUT-707): Android 는
     * {@code okhttp/4.x}, iOS 는 {@code LevelUpTogether/33 CFNetwork/… Darwin/…}. 네이티브 호출(푸시 토큰
     * 등록·토큰 재발급·구독 검증)은 {@code X-Device-Type} 없이 오는데, 이 UA 를 web 으로 폴백하면 앱 유저가 매일 web 활동으로도 적립돼 DAU
     * web 이 부풀었다. iPad 네이티브도 ios 로 본다 — RN {@code Platform.OS} 와 같다.
     */
    public String fromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return WEB;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("ipad")) {
            return IPAD;
        }
        if (ua.contains("iphone") || ua.contains("ipod")) {
            return IOS;
        }
        if (ua.contains("android")) {
            return ANDROID;
        }
        if (ua.contains("okhttp")) {
            return ANDROID;
        }
        if (ua.contains("cfnetwork") || ua.contains("darwin")) {
            return IOS;
        }
        return WEB;
    }

    /** 네이티브 앱(ios/ipad/android) 여부 — deviceId 생성 분기용 */
    public boolean isNative(String deviceType) {
        return IOS.equals(deviceType) || IPAD.equals(deviceType) || ANDROID.equals(deviceType);
    }
}
