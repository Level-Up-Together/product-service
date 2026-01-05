package io.pinkspider.global.translation;

import io.pinkspider.global.translation.enums.SupportedLocale;

/**
 * 다국어 필드 선택 유틸리티
 * DB 다국어 컬럼 방식에서 locale에 따라 적절한 값을 반환
 */
public class LocaleUtils {

    private LocaleUtils() {
        // Utility class
    }

    /**
     * locale에 따라 적절한 텍스트 반환
     * - 해당 locale 값이 있으면 반환
     * - 없으면 기본값(한국어) 반환
     *
     * @param defaultValue 기본값 (한국어)
     * @param enValue 영어 값
     * @param arValue 아랍어 값
     * @param locale 대상 locale (Accept-Language 헤더에서 추출)
     * @return 적절한 locale의 텍스트
     */
    public static String getLocalizedText(String defaultValue, String enValue, String arValue, String locale) {
        if (locale == null || locale.isBlank()) {
            return defaultValue;
        }

        String langCode = SupportedLocale.extractLanguageCode(locale);

        return switch (langCode) {
            case "en" -> enValue != null && !enValue.isBlank() ? enValue : defaultValue;
            case "ar" -> arValue != null && !arValue.isBlank() ? arValue : defaultValue;
            default -> defaultValue;
        };
    }

    /**
     * 단순 2개 언어 버전 (기본값 + 영어만)
     */
    public static String getLocalizedText(String defaultValue, String enValue, String locale) {
        return getLocalizedText(defaultValue, enValue, null, locale);
    }
}
