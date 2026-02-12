package io.pinkspider.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StringUtils 단위 테스트")
class StringUtilsTest {

    @Nested
    @DisplayName("getRpadString 테스트")
    class GetRpadStringTest {

        @Test
        @DisplayName("문자열을 오른쪽 패딩한다")
        void getRpadString_padsRight() throws UnsupportedEncodingException {
            // given
            String input = "ABC";
            int byteLength = 10;

            // when
            String result = StringUtils.getRpadString(input, byteLength);

            // then
            assertThat(result).hasSize(10);
            assertThat(result).startsWith("ABC");
        }

        @Test
        @DisplayName("null 입력시 공백으로 대체한다")
        void getRpadString_nullInput_replacesWithSpace() throws UnsupportedEncodingException {
            // when
            String result = StringUtils.getRpadString(null, 5);

            // then
            assertThat(result).hasSize(5);
        }

        @Test
        @DisplayName("한글 문자열도 처리한다")
        void getRpadString_koreanString() throws UnsupportedEncodingException {
            // given
            String input = "한글";
            int byteLength = 10;

            // when
            String result = StringUtils.getRpadString(input, byteLength);

            // then
            assertThat(result).isNotNull();
            assertThat(result).startsWith("한글");
        }

        @Test
        @DisplayName("입력 길이가 지정 바이트 길이보다 길면 자른다")
        void getRpadString_truncatesLongString() throws UnsupportedEncodingException {
            // given
            String input = "ABCDEFGHIJ";
            int byteLength = 5;

            // when
            String result = StringUtils.getRpadString(input, byteLength);

            // then
            assertThat(result.getBytes("EUC-KR")).hasSize(5);
        }
    }

    @Nested
    @DisplayName("getLpadString 테스트")
    class GetLpadStringTest {

        @Test
        @DisplayName("문자열을 왼쪽 제로 패딩한다")
        void getLpadString_padsLeftWithZeros() throws UnsupportedEncodingException {
            // given
            String input = "123";
            int byteLength = 7;

            // when
            String result = StringUtils.getLpadString(input, byteLength);

            // then
            assertThat(result).isEqualTo("0000123");
            assertThat(result).hasSize(7);
        }

        @Test
        @DisplayName("null 입력시 공백으로 대체한다")
        void getLpadString_nullInput_replacesWithSpace() throws UnsupportedEncodingException {
            // when
            String result = StringUtils.getLpadString(null, 5);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("입력 길이와 같으면 패딩 없이 반환한다")
        void getLpadString_sameLength_noPadding() throws UnsupportedEncodingException {
            // given
            String input = "12345";
            int byteLength = 5;

            // when
            String result = StringUtils.getLpadString(input, byteLength);

            // then
            assertThat(result).isEqualTo("12345");
        }

        @Test
        @DisplayName("숫자형 문자열을 제로 패딩한다")
        void getLpadString_numericString() throws UnsupportedEncodingException {
            // given
            String input = "42";
            int byteLength = 6;

            // when
            String result = StringUtils.getLpadString(input, byteLength);

            // then
            assertThat(result).isEqualTo("000042");
        }
    }
}
