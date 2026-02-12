package io.pinkspider.global.translation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 번역 정보 DTO
 * API 응답에 포함되는 번역 결과 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TranslationInfo {

    /**
     * 번역된 내용
     */
    @JsonProperty("content")
    private String content;

    /**
     * 번역된 제목 (있는 경우)
     */
    @JsonProperty("title")
    private String title;

    /**
     * 원문 언어 코드
     */
    @JsonProperty("source_locale")
    private String sourceLocale;

    /**
     * 대상 언어 코드
     */
    @JsonProperty("target_locale")
    private String targetLocale;

    /**
     * 번역 여부
     * false: 원문과 동일 언어이거나 번역이 불필요한 경우
     */
    @JsonProperty("is_translated")
    private boolean isTranslated;

    /**
     * 번역되지 않은 원문 반환용 팩토리 메서드
     */
    public static TranslationInfo notTranslated(String sourceLocale) {
        return TranslationInfo.builder()
            .sourceLocale(sourceLocale)
            .isTranslated(false)
            .build();
    }

    /**
     * 번역 결과 반환용 팩토리 메서드
     */
    public static TranslationInfo translated(String content, String sourceLocale, String targetLocale) {
        return TranslationInfo.builder()
            .content(content)
            .sourceLocale(sourceLocale)
            .targetLocale(targetLocale)
            .isTranslated(true)
            .build();
    }

    /**
     * 제목과 내용 모두 번역된 결과 반환용 팩토리 메서드
     */
    public static TranslationInfo translated(String title, String content, String sourceLocale, String targetLocale) {
        return TranslationInfo.builder()
            .title(title)
            .content(content)
            .sourceLocale(sourceLocale)
            .targetLocale(targetLocale)
            .isTranslated(true)
            .build();
    }
}
