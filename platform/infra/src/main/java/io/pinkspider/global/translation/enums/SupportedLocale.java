package io.pinkspider.global.translation.enums;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 지원 언어 목록
 */
@Getter
@RequiredArgsConstructor
public enum SupportedLocale {
    KOREAN("ko", "한국어"),
    ENGLISH("en", "English"),
    ARABIC("ar", "العربية");

    private final String code;
    private final String displayName;

    /**
     * 기본 언어 (한국어)
     */
    public static final SupportedLocale DEFAULT = KOREAN;

    /**
     * 언어 코드로 SupportedLocale 조회
     */
    public static Optional<SupportedLocale> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(locale -> locale.getCode().equalsIgnoreCase(code))
            .findFirst();
    }

    /**
     * 지원하는 언어인지 확인
     */
    public static boolean isSupported(String code) {
        return fromCode(code).isPresent();
    }

    /**
     * Accept-Language 헤더에서 언어 코드 추출
     * 예: "ko-KR,ko;q=0.9,en;q=0.8" -> "ko"
     */
    public static String extractLanguageCode(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT.getCode();
        }

        // 첫 번째 언어 코드 추출 (ko-KR -> ko)
        String[] parts = acceptLanguage.split(",")[0].split("-");
        String primaryLang = parts[0].trim().toLowerCase();

        // 지원하는 언어인지 확인
        return fromCode(primaryLang)
            .map(SupportedLocale::getCode)
            .orElse(DEFAULT.getCode());
    }
}
