package io.pinkspider.global.validation;

import org.springframework.stereotype.Component;

/**
 * 한글 텍스트 정규화 및 초성 추출 유틸리티
 */
@Component
public class KoreanTextNormalizer {

    // 한글 유니코드 범위
    private static final int HANGUL_BASE = 0xAC00;  // '가'
    private static final int HANGUL_END = 0xD7A3;   // '힣'

    // 초성 (Chosung) 배열 - 19개
    private static final char[] CHOSUNG = {
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ',
        'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    // 중성 개수: 21, 종성 개수: 28
    private static final int JUNGSUNG_COUNT = 21;
    private static final int JONGSUNG_COUNT = 28;

    /**
     * 텍스트 정규화: 비교를 위한 전처리
     * - 소문자 변환
     * - 공백 및 특수문자 제거
     * - 한글, 영문, 숫자, 자모만 유지
     *
     * @param text 원본 텍스트
     * @return 정규화된 텍스트
     */
    public String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text.toLowerCase()
            .replaceAll("[^가-힣a-z0-9ㄱ-ㅎㅏ-ㅣ]", "");
    }

    /**
     * 한글 초성(Chosung) 추출
     * 예: "안녕하세요" → "ㅇㄴㅎㅅㅇ"
     * 예: "시발" → "ㅅㅂ"
     *
     * @param text 원본 텍스트
     * @return 초성만 추출된 문자열
     */
    public String extractChosung(String text) {
        if (text == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (isKoreanSyllable(c)) {
                // 완성형 한글 (가-힣)에서 초성 추출
                int index = (c - HANGUL_BASE) / (JUNGSUNG_COUNT * JONGSUNG_COUNT);
                result.append(CHOSUNG[index]);
            } else if (isKoreanJamo(c)) {
                // 이미 자모인 경우 그대로 추가
                result.append(c);
            }
            // 한글이 아닌 문자는 무시
        }

        return result.toString();
    }

    /**
     * 완성형 한글 여부 확인 (가-힣)
     *
     * @param c 문자
     * @return 완성형 한글이면 true
     */
    public boolean isKoreanSyllable(char c) {
        return c >= HANGUL_BASE && c <= HANGUL_END;
    }

    /**
     * 한글 자모 여부 확인 (ㄱ-ㅎ, ㅏ-ㅣ)
     *
     * @param c 문자
     * @return 자모면 true
     */
    public boolean isKoreanJamo(char c) {
        return (c >= 0x3131 && c <= 0x3163);  // ㄱ-ㅣ
    }

    /**
     * 한글 초성 여부 확인 (ㄱ-ㅎ)
     *
     * @param c 문자
     * @return 초성이면 true
     */
    public boolean isKoreanChosung(char c) {
        return (c >= 0x3131 && c <= 0x314E);  // ㄱ-ㅎ
    }

    /**
     * 레벤슈타인 거리 계산 (편집 거리)
     * 두 문자열의 유사도를 측정
     *
     * @param s1 문자열 1
     * @param s2 문자열 2
     * @return 편집 거리 (낮을수록 유사)
     */
    public int levenshteinDistance(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";

        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        // 초기화
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        // DP 계산
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(
                        dp[i - 1][j] + 1,      // 삭제
                        dp[i][j - 1] + 1       // 삽입
                    ),
                    dp[i - 1][j - 1] + cost    // 대체
                );
            }
        }

        return dp[len1][len2];
    }

    /**
     * 텍스트에서 한글만 추출
     *
     * @param text 원본 텍스트
     * @return 한글만 포함된 문자열
     */
    public String extractKoreanOnly(String text) {
        if (text == null) {
            return "";
        }

        return text.replaceAll("[^가-힣ㄱ-ㅎㅏ-ㅣ]", "");
    }
}
