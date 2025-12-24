package io.pinkspider.leveluptogethermvp.profanity.application;

import io.pinkspider.global.validation.KoreanTextNormalizer;
import io.pinkspider.global.validation.ProfanityDetectionMode;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityDetectionResult;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 비속어 탐지 엔진
 * 다양한 모드로 비속어를 탐지하는 핵심 로직
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfanityDetectionEngine {

    private final ProfanityValidationService profanityValidationService;
    private final KoreanTextNormalizer normalizer;

    /**
     * 비속어 탐지 수행
     *
     * @param content 검사할 컨텐츠
     * @param mode 탐지 모드
     * @param checkKoreanJamo 한글 초성 검사 여부
     * @param levenshteinThreshold 레벤슈타인 거리 임계값 (0이면 비활성)
     * @return 탐지 결과
     */
    public ProfanityDetectionResult detect(
            String content,
            ProfanityDetectionMode mode,
            boolean checkKoreanJamo,
            int levenshteinThreshold) {

        if (content == null || content.trim().isEmpty()) {
            return ProfanityDetectionResult.notDetected();
        }

        Set<String> profanityWords = profanityValidationService.getActiveProfanityWords();

        if (profanityWords.isEmpty()) {
            return ProfanityDetectionResult.notDetected();
        }

        return switch (mode) {
            case LENIENT -> detectLenient(content, profanityWords);
            case NORMAL -> detectNormal(content, profanityWords, checkKoreanJamo);
            case STRICT -> detectStrict(content, profanityWords, checkKoreanJamo, levenshteinThreshold);
        };
    }

    /**
     * LENIENT 모드: 단순 String.contains() 검사
     */
    private ProfanityDetectionResult detectLenient(String content, Set<String> profanityWords) {
        for (String word : profanityWords) {
            if (content.contains(word)) {
                log.debug("LENIENT_MATCH 탐지: '{}' in content", word);
                return ProfanityDetectionResult.detected(word, "LENIENT_MATCH");
            }
        }
        return ProfanityDetectionResult.notDetected();
    }

    /**
     * NORMAL 모드: 정규화 + 초성 검사
     * - 대소문자 무시
     * - 공백/특수문자 제거
     * - 한글 초성 매칭
     */
    private ProfanityDetectionResult detectNormal(
            String content, Set<String> profanityWords, boolean checkKoreanJamo) {

        String normalizedContent = normalizer.normalize(content);

        for (String word : profanityWords) {
            String normalizedWord = normalizer.normalize(word);

            // 정규화된 텍스트에서 매칭
            if (!normalizedWord.isEmpty() && normalizedContent.contains(normalizedWord)) {
                log.debug("NORMALIZED_MATCH 탐지: '{}' (정규화: '{}') in content", word, normalizedWord);
                return ProfanityDetectionResult.detected(word, "NORMALIZED_MATCH");
            }

            // 한글 초성 검사
            if (checkKoreanJamo) {
                String contentChosung = normalizer.extractChosung(content);
                String wordChosung = normalizer.extractChosung(word);

                // 금칙어가 초성만으로 이루어진 경우 (예: ㅅㅂ)
                String wordChosungFromOriginal = normalizer.extractChosung(
                    normalizer.extractKoreanOnly(word)
                );

                if (!wordChosung.isEmpty() && contentChosung.contains(wordChosung)) {
                    log.debug("CHOSUNG_MATCH 탐지: '{}' (초성: '{}') in content chosung '{}'",
                        word, wordChosung, contentChosung);
                    return ProfanityDetectionResult.detected(word, "CHOSUNG_MATCH");
                }

                // 원본이 이미 초성인 경우도 검사 (예: content에 "ㅅㅂ" 직접 입력)
                String contentNormalized = normalizer.normalize(content);
                if (!wordChosungFromOriginal.isEmpty()
                    && contentNormalized.contains(wordChosungFromOriginal)) {
                    log.debug("CHOSUNG_DIRECT_MATCH 탐지: '{}' in normalized content", word);
                    return ProfanityDetectionResult.detected(word, "CHOSUNG_MATCH");
                }
            }
        }

        return ProfanityDetectionResult.notDetected();
    }

    /**
     * STRICT 모드: NORMAL + 레벤슈타인 거리 검사 (오타 탐지)
     */
    private ProfanityDetectionResult detectStrict(
            String content, Set<String> profanityWords,
            boolean checkKoreanJamo, int levenshteinThreshold) {

        // 먼저 NORMAL 모드 검사
        ProfanityDetectionResult normalResult = detectNormal(content, profanityWords, checkKoreanJamo);
        if (normalResult.isDetected()) {
            return normalResult;
        }

        // 레벤슈타인 거리 검사 (threshold > 0인 경우)
        if (levenshteinThreshold > 0) {
            String normalizedContent = normalizer.normalize(content);

            for (String word : profanityWords) {
                String normalizedWord = normalizer.normalize(word);

                if (normalizedWord.isEmpty()) {
                    continue;
                }

                // 슬라이딩 윈도우로 부분 문자열 검사
                int windowSize = normalizedWord.length();
                for (int i = 0; i <= normalizedContent.length() - windowSize; i++) {
                    int end = Math.min(i + windowSize + levenshteinThreshold, normalizedContent.length());
                    String substring = normalizedContent.substring(i, end);

                    int distance = normalizer.levenshteinDistance(substring, normalizedWord);
                    if (distance > 0 && distance <= levenshteinThreshold) {
                        log.debug("LEVENSHTEIN_MATCH 탐지: '{}' (distance: {}) in substring '{}'",
                            word, distance, substring);
                        return ProfanityDetectionResult.detected(word, "LEVENSHTEIN_MATCH", distance);
                    }
                }
            }
        }

        return ProfanityDetectionResult.notDetected();
    }
}
