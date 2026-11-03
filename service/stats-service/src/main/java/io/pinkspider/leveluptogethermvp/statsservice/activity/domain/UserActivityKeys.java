package io.pinkspider.leveluptogethermvp.statsservice.activity.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * LUT-598: 활동 신호 Redis 키 규칙. 날짜 경계는 KST — 다른 스케줄러·길드 포인트와 같다.
 *
 * <ul>
 *   <li>{@code active:{yyyy-MM-dd}} — 그날 인증된 API 요청을 보낸 userId 집합
 *   <li>{@code active:{yyyy-MM-dd}:{deviceType}} — 기기 종류별 부분 집합 (web/ios/ipad/android)
 * </ul>
 *
 * <p>flush(00:20 KST)는 키를 지우지 않는다. TTL 로 자연 소멸하므로 flush 실패·재실행에 안전하다.
 */
public final class UserActivityKeys {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final String PREFIX = "active:";
    public static final Duration TTL = Duration.ofDays(40);

    /** 기기 종류 — {@code DeviceTypeResolver} 가 돌려주는 4가지 */
    public static final List<String> DEVICE_TYPES = List.of("web", "ios", "ipad", "android");

    private UserActivityKeys() {}

    public static String dailyKey(LocalDate date) {
        return PREFIX + date;
    }

    public static String deviceKey(LocalDate date, String deviceType) {
        return PREFIX + date + ":" + deviceType;
    }
}
