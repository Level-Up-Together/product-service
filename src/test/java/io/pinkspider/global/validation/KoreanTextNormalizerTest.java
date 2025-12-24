package io.pinkspider.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KoreanTextNormalizerTest {

    private KoreanTextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new KoreanTextNormalizer();
    }

    @Nested
    @DisplayName("normalize 메서드")
    class NormalizeTest {

        @Test
        @DisplayName("소문자 변환이 올바르게 동작한다")
        void convertToLowerCase() {
            String result = normalizer.normalize("HELLO World");
            assertThat(result).isEqualTo("helloworld");
        }

        @Test
        @DisplayName("공백이 제거된다")
        void removeSpaces() {
            String result = normalizer.normalize("hello world");
            assertThat(result).isEqualTo("helloworld");
        }

        @Test
        @DisplayName("특수문자가 제거된다")
        void removeSpecialCharacters() {
            String result = normalizer.normalize("hello!@#$%world");
            assertThat(result).isEqualTo("helloworld");
        }

        @Test
        @DisplayName("한글은 유지된다")
        void keepKorean() {
            String result = normalizer.normalize("안녕하세요");
            assertThat(result).isEqualTo("안녕하세요");
        }

        @Test
        @DisplayName("한글 자모음은 유지된다")
        void keepKoreanJamo() {
            String result = normalizer.normalize("ㅅㅂ ㅂㅅ");
            assertThat(result).isEqualTo("ㅅㅂㅂㅅ");
        }

        @Test
        @DisplayName("숫자는 유지된다")
        void keepNumbers() {
            String result = normalizer.normalize("hello123world");
            assertThat(result).isEqualTo("hello123world");
        }

        @Test
        @DisplayName("복합 텍스트가 올바르게 정규화된다")
        void normalizeComplexText() {
            String result = normalizer.normalize("안녕! Hello 123 World!");
            assertThat(result).isEqualTo("안녕hello123world");
        }

        @Test
        @DisplayName("null 입력 시 빈 문자열 반환")
        void returnEmptyForNull() {
            String result = normalizer.normalize(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("빈 문자열 입력 시 빈 문자열 반환")
        void returnEmptyForEmpty() {
            String result = normalizer.normalize("");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractChosung 메서드")
    class ExtractChosungTest {

        @Test
        @DisplayName("한글 초성이 올바르게 추출된다")
        void extractKoreanChosung() {
            String result = normalizer.extractChosung("시발");
            assertThat(result).isEqualTo("ㅅㅂ");
        }

        @Test
        @DisplayName("긴 한글 문장의 초성이 추출된다")
        void extractLongKoreanChosung() {
            String result = normalizer.extractChosung("안녕하세요");
            assertThat(result).isEqualTo("ㅇㄴㅎㅅㅇ");
        }

        @Test
        @DisplayName("병신의 초성이 추출된다")
        void extractBsByeongsinChosung() {
            String result = normalizer.extractChosung("병신");
            assertThat(result).isEqualTo("ㅂㅅ");
        }

        @Test
        @DisplayName("영어는 그대로 유지된다")
        void keepEnglish() {
            String result = normalizer.extractChosung("hello안녕");
            assertThat(result).isEqualTo("helloㅇㄴ");
        }

        @Test
        @DisplayName("숫자는 그대로 유지된다")
        void keepNumbers() {
            String result = normalizer.extractChosung("안녕123");
            assertThat(result).isEqualTo("ㅇㄴ123");
        }

        @Test
        @DisplayName("이미 초성인 문자는 그대로 유지된다")
        void keepExistingChosung() {
            String result = normalizer.extractChosung("ㅅㅂ");
            assertThat(result).isEqualTo("ㅅㅂ");
        }

        @Test
        @DisplayName("null 입력 시 빈 문자열 반환")
        void returnEmptyForNull() {
            String result = normalizer.extractChosung(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("빈 문자열 입력 시 빈 문자열 반환")
        void returnEmptyForEmpty() {
            String result = normalizer.extractChosung("");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("levenshteinDistance 메서드")
    class LevenshteinDistanceTest {

        @Test
        @DisplayName("동일한 문자열의 거리는 0이다")
        void sameStringsHaveZeroDistance() {
            int distance = normalizer.levenshteinDistance("hello", "hello");
            assertThat(distance).isZero();
        }

        @Test
        @DisplayName("한 글자 차이의 거리는 1이다")
        void oneCharDifferenceHasDistanceOne() {
            int distance = normalizer.levenshteinDistance("hello", "hallo");
            assertThat(distance).isEqualTo(1);
        }

        @Test
        @DisplayName("완전히 다른 문자열의 거리를 계산한다")
        void calculateDistanceForDifferentStrings() {
            int distance = normalizer.levenshteinDistance("abc", "xyz");
            assertThat(distance).isEqualTo(3);
        }

        @Test
        @DisplayName("빈 문자열과의 거리는 다른 문자열의 길이이다")
        void emptyStringDistanceIsLength() {
            int distance = normalizer.levenshteinDistance("", "hello");
            assertThat(distance).isEqualTo(5);
        }

        @Test
        @DisplayName("두 빈 문자열의 거리는 0이다")
        void twoEmptyStringsHaveZeroDistance() {
            int distance = normalizer.levenshteinDistance("", "");
            assertThat(distance).isZero();
        }

        @Test
        @DisplayName("한글 문자열의 거리를 계산한다")
        void calculateKoreanDistance() {
            int distance = normalizer.levenshteinDistance("시발", "시팔");
            assertThat(distance).isEqualTo(1);
        }
    }
}
