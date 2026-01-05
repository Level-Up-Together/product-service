package io.pinkspider.global.translation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Google Cloud Translation API v2 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleTranslationResponse {

    @JsonProperty("data")
    private TranslationData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranslationData {

        @JsonProperty("translations")
        private List<Translation> translations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Translation {

        /**
         * 번역된 텍스트
         */
        @JsonProperty("translatedText")
        private String translatedText;

        /**
         * 감지된 원본 언어 (source 미지정시)
         */
        @JsonProperty("detectedSourceLanguage")
        private String detectedSourceLanguage;
    }

    /**
     * 첫 번째 번역 결과 반환
     */
    public String getFirstTranslatedText() {
        if (data != null && data.getTranslations() != null && !data.getTranslations().isEmpty()) {
            return data.getTranslations().get(0).getTranslatedText();
        }
        return null;
    }

    /**
     * 감지된 원본 언어 반환
     */
    public String getDetectedSourceLanguage() {
        if (data != null && data.getTranslations() != null && !data.getTranslations().isEmpty()) {
            return data.getTranslations().get(0).getDetectedSourceLanguage();
        }
        return null;
    }

    /**
     * 모든 번역 결과 텍스트 반환
     */
    public List<String> getAllTranslatedTexts() {
        if (data != null && data.getTranslations() != null) {
            return data.getTranslations().stream()
                .map(Translation::getTranslatedText)
                .toList();
        }
        return List.of();
    }
}
