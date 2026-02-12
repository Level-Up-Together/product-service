package io.pinkspider.global.translation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Google Cloud Translation API v2 요청 DTO
 * https://cloud.google.com/translate/docs/reference/rest/v2/translate
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleTranslationRequest {

    /**
     * 번역할 텍스트 목록
     */
    @JsonProperty("q")
    private List<String> queries;

    /**
     * 대상 언어 코드 (ISO 639-1)
     * 예: en, ko, ar
     */
    @JsonProperty("target")
    private String targetLanguage;

    /**
     * 원본 언어 코드 (선택사항, 자동 감지됨)
     */
    @JsonProperty("source")
    private String sourceLanguage;

    /**
     * 응답 형식
     * text: 일반 텍스트 (기본값)
     * html: HTML 태그 보존
     */
    @JsonProperty("format")
    private String format;

    public static GoogleTranslationRequest of(String text, String targetLanguage) {
        return GoogleTranslationRequest.builder()
            .queries(List.of(text))
            .targetLanguage(targetLanguage)
            .format("text")
            .build();
    }

    public static GoogleTranslationRequest of(String text, String sourceLanguage, String targetLanguage) {
        return GoogleTranslationRequest.builder()
            .queries(List.of(text))
            .sourceLanguage(sourceLanguage)
            .targetLanguage(targetLanguage)
            .format("text")
            .build();
    }

    public static GoogleTranslationRequest of(List<String> texts, String targetLanguage) {
        return GoogleTranslationRequest.builder()
            .queries(texts)
            .targetLanguage(targetLanguage)
            .format("text")
            .build();
    }
}
