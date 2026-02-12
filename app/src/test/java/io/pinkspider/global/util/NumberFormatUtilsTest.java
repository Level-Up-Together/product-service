package io.pinkspider.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NumberFormatUtils 단위 테스트")
class NumberFormatUtilsTest {

    @Nested
    @DisplayName("comma(Long) 테스트")
    class CommaLongTest {

        @Test
        @DisplayName("Long 값을 천단위 콤마 형식으로 변환한다")
        void comma_long_success() {
            // given
            Long value = 1234567L;

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("1,234,567");
        }

        @Test
        @DisplayName("0을 포맷한다")
        void comma_long_zero() {
            // given
            Long value = 0L;

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("0");
        }

        @Test
        @DisplayName("작은 숫자는 콤마 없이 반환한다")
        void comma_long_small() {
            // given
            Long value = 999L;

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("999");
        }

        @Test
        @DisplayName("정확히 천 단위에서 콤마를 추가한다")
        void comma_long_thousand() {
            // given
            Long value = 1000L;

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("1,000");
        }

        @Test
        @DisplayName("큰 숫자를 포맷한다")
        void comma_long_largeNumber() {
            // given
            Long value = 9876543210L;

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("9,876,543,210");
        }
    }

    @Nested
    @DisplayName("comma(Integer) 테스트")
    class CommaIntegerTest {

        @Test
        @DisplayName("Integer 값을 천단위 콤마 형식으로 변환한다")
        void comma_integer_success() {
            // given
            Integer value = 1234567;

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("1,234,567");
        }

        @Test
        @DisplayName("0을 포맷한다")
        void comma_integer_zero() {
            // given
            Integer value = 0;

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("0");
        }

        @Test
        @DisplayName("음수를 포맷한다")
        void comma_integer_negative() {
            // given
            Integer value = -1234567;

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("-1,234,567");
        }
    }

    @Nested
    @DisplayName("comma(Object) 테스트")
    class CommaObjectTest {

        @Test
        @DisplayName("null 값은 '0'을 반환한다")
        void comma_object_null() {
            // when
            String result = NumberFormatUtils.comma((Object) null);

            // then
            assertThat(result).isEqualTo("0");
        }

        @Test
        @DisplayName("String 숫자를 포맷한다")
        void comma_object_string() {
            // given
            Object value = "1234567";

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("1,234,567");
        }

        @Test
        @DisplayName("Double 값을 포맷한다")
        void comma_object_double() {
            // given
            Object value = 1234567.89;

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("1,234,568");
        }
    }

    @Nested
    @DisplayName("comma(BigDecimal) 테스트")
    class CommaBigDecimalTest {

        @Test
        @DisplayName("BigDecimal 값을 천단위 콤마 형식으로 변환한다")
        void comma_bigDecimal_success() {
            // given
            BigDecimal value = new BigDecimal("1234567.89");

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("1,234,568");
        }

        @Test
        @DisplayName("정수 BigDecimal을 포맷한다")
        void comma_bigDecimal_integer() {
            // given
            BigDecimal value = new BigDecimal("9999999");

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("9,999,999");
        }

        @Test
        @DisplayName("0 BigDecimal을 포맷한다")
        void comma_bigDecimal_zero() {
            // given
            BigDecimal value = BigDecimal.ZERO;

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("0");
        }

        @Test
        @DisplayName("음수 BigDecimal을 포맷한다")
        void comma_bigDecimal_negative() {
            // given
            BigDecimal value = new BigDecimal("-1234567.50");

            // when
            String result = NumberFormatUtils.comma(value);

            // then
            assertThat(result).isEqualTo("-1,234,568");
        }
    }
}
