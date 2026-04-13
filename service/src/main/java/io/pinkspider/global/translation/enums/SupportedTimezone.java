package io.pinkspider.global.translation.enums;

import java.time.ZoneId;
import java.util.Map;

/**
 * 지원 타임존 유틸리티.
 * locale 및 국가코드(ISO 3166-1 alpha-2)로부터 기본 타임존을 추론한다.
 */
public final class SupportedTimezone {

    private SupportedTimezone() {
    }

    public static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    /**
     * locale → 기본 타임존 매핑
     */
    private static final Map<String, String> LOCALE_TO_TIMEZONE = Map.of(
        "ko", "Asia/Seoul",
        "ja", "Asia/Tokyo",
        "ar", "Asia/Riyadh",
        "en", "UTC"
    );

    /**
     * ISO 3166-1 alpha-2 국가코드 → 타임존 매핑 (주요 국가)
     */
    private static final Map<String, String> COUNTRY_TO_TIMEZONE = Map.ofEntries(
        Map.entry("KR", "Asia/Seoul"),
        Map.entry("JP", "Asia/Tokyo"),
        Map.entry("SA", "Asia/Riyadh"),
        Map.entry("AE", "Asia/Dubai"),
        Map.entry("EG", "Africa/Cairo"),
        Map.entry("QA", "Asia/Qatar"),
        Map.entry("KW", "Asia/Kuwait"),
        Map.entry("BH", "Asia/Bahrain"),
        Map.entry("OM", "Asia/Muscat"),
        Map.entry("IQ", "Asia/Baghdad"),
        Map.entry("JO", "Asia/Amman"),
        Map.entry("US", "America/New_York"),
        Map.entry("GB", "Europe/London"),
        Map.entry("DE", "Europe/Berlin"),
        Map.entry("FR", "Europe/Paris"),
        Map.entry("CN", "Asia/Shanghai"),
        Map.entry("TW", "Asia/Taipei"),
        Map.entry("IN", "Asia/Kolkata"),
        Map.entry("AU", "Australia/Sydney"),
        Map.entry("SG", "Asia/Singapore")
    );

    /**
     * 유효한 IANA timezone ID인지 확인
     */
    public static boolean isValid(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return false;
        }
        return ZoneId.getAvailableZoneIds().contains(timezone);
    }

    /**
     * locale 코드로부터 기본 타임존 추론
     */
    public static String fromLocale(String localeCode) {
        if (localeCode == null) {
            return DEFAULT_TIMEZONE;
        }
        return LOCALE_TO_TIMEZONE.getOrDefault(localeCode.toLowerCase(), DEFAULT_TIMEZONE);
    }

    /**
     * ISO 3166-1 alpha-2 국가코드로부터 타임존 추론
     */
    public static String fromCountryCode(String countryCode) {
        if (countryCode == null) {
            return DEFAULT_TIMEZONE;
        }
        return COUNTRY_TO_TIMEZONE.getOrDefault(countryCode.toUpperCase(), DEFAULT_TIMEZONE);
    }

    /**
     * 타임존 결정 우선순위:
     * 1. 클라이언트가 직접 전달한 timezone (유효한 경우)
     * 2. 국가코드 기반 추론
     * 3. locale 기반 추론
     * 4. 기본값 (Asia/Seoul)
     */
    public static String resolve(String clientTimezone, String countryCode, String localeCode) {
        if (clientTimezone != null && isValid(clientTimezone)) {
            return clientTimezone;
        }
        if (countryCode != null) {
            String fromCountry = COUNTRY_TO_TIMEZONE.get(countryCode.toUpperCase());
            if (fromCountry != null) {
                return fromCountry;
            }
        }
        return fromLocale(localeCode);
    }
}
