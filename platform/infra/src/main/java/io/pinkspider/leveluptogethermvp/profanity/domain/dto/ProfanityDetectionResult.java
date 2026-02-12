package io.pinkspider.leveluptogethermvp.profanity.domain.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 비속어 탐지 결과 DTO
 */
@Getter
@Builder
public class ProfanityDetectionResult {

    /**
     * 비속어 탐지 여부
     */
    private final boolean detected;

    /**
     * 탐지된 비속어
     */
    private final String detectedWord;

    /**
     * 매칭 유형 (LENIENT_MATCH, NORMALIZED_MATCH, CHOSUNG_MATCH, LEVENSHTEIN_MATCH)
     */
    private final String matchType;

    /**
     * 레벤슈타인 거리 (LEVENSHTEIN_MATCH인 경우에만 유효)
     */
    private final int levenshteinDistance;

    /**
     * 탐지되지 않은 결과 생성
     */
    public static ProfanityDetectionResult notDetected() {
        return ProfanityDetectionResult.builder()
            .detected(false)
            .build();
    }

    /**
     * 탐지된 결과 생성
     *
     * @param word 탐지된 비속어
     * @param matchType 매칭 유형
     */
    public static ProfanityDetectionResult detected(String word, String matchType) {
        return ProfanityDetectionResult.builder()
            .detected(true)
            .detectedWord(word)
            .matchType(matchType)
            .levenshteinDistance(0)
            .build();
    }

    /**
     * 레벤슈타인 거리 포함 탐지 결과 생성
     *
     * @param word 탐지된 비속어
     * @param matchType 매칭 유형
     * @param distance 레벤슈타인 거리
     */
    public static ProfanityDetectionResult detected(String word, String matchType, int distance) {
        return ProfanityDetectionResult.builder()
            .detected(true)
            .detectedWord(word)
            .matchType(matchType)
            .levenshteinDistance(distance)
            .build();
    }
}
