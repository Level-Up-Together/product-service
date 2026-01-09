package io.pinkspider.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DateUtils 단위 테스트")
class DateUtilsTest {

    private DateUtils dateUtils;

    @BeforeEach
    void setUp() {
        dateUtils = new DateUtils();
    }

    @Nested
    @DisplayName("convertDateFormat 테스트")
    class ConvertDateFormatTest {

        @Test
        @DisplayName("LocalDateTime을 지정된 형식으로 변환한다")
        void convertDateFormat_success() {
            // given
            LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
            String format = "yyyy-MM-dd HH:mm:ss";

            // when
            String result = DateUtils.convertDateFormat(dateTime, format);

            // then
            assertThat(result).isEqualTo("2024-01-15 10:30:00");
        }

        @Test
        @DisplayName("날짜만 포함하는 형식으로 변환한다")
        void convertDateFormat_dateOnly() {
            // given
            LocalDateTime dateTime = LocalDateTime.of(2024, 12, 25, 14, 45, 30);
            String format = "yyyy/MM/dd";

            // when
            String result = DateUtils.convertDateFormat(dateTime, format);

            // then
            assertThat(result).isEqualTo("2024/12/25");
        }

        @Test
        @DisplayName("한국어 형식으로 변환한다")
        void convertDateFormat_koreanFormat() {
            // given
            LocalDateTime dateTime = LocalDateTime.of(2024, 5, 1, 9, 0, 0);
            String format = "yyyy년 MM월 dd일";

            // when
            String result = DateUtils.convertDateFormat(dateTime, format);

            // then
            assertThat(result).isEqualTo("2024년 05월 01일");
        }
    }

    @Nested
    @DisplayName("convertStringToLocalDateTime 테스트")
    class ConvertStringToLocalDateTimeTest {

        @Test
        @DisplayName("문자열을 LocalDateTime으로 변환한다")
        void convertStringToLocalDateTime_success() {
            // given
            String dateTimeStr = "2024-01-15 10:30:00";
            String format = "yyyy-MM-dd HH:mm:ss";

            // when
            LocalDateTime result = dateUtils.convertStringToLocalDateTime(dateTimeStr, format);

            // then
            assertThat(result.getYear()).isEqualTo(2024);
            assertThat(result.getMonthValue()).isEqualTo(1);
            assertThat(result.getDayOfMonth()).isEqualTo(15);
            assertThat(result.getHour()).isEqualTo(10);
            assertThat(result.getMinute()).isEqualTo(30);
        }

        @Test
        @DisplayName("다양한 형식의 날짜 문자열을 변환한다")
        void convertStringToLocalDateTime_variousFormats() {
            // given
            String dateTimeStr = "2024/12/25 14:45:30";
            String format = "yyyy/MM/dd HH:mm:ss";

            // when
            LocalDateTime result = dateUtils.convertStringToLocalDateTime(dateTimeStr, format);

            // then
            assertThat(result.getYear()).isEqualTo(2024);
            assertThat(result.getMonthValue()).isEqualTo(12);
            assertThat(result.getDayOfMonth()).isEqualTo(25);
        }

        @Test
        @DisplayName("형식이 맞지 않으면 예외가 발생한다")
        void convertStringToLocalDateTime_invalidFormat_throws() {
            // given
            String dateTimeStr = "2024-01-15";
            String format = "yyyy-MM-dd HH:mm:ss";

            // when & then
            assertThatThrownBy(() -> dateUtils.convertStringToLocalDateTime(dateTimeStr, format))
                .isInstanceOf(DateTimeParseException.class);
        }
    }

    @Nested
    @DisplayName("convertStringToLocalDateTimeWithZone 테스트")
    class ConvertStringToLocalDateTimeWithZoneTest {

        @Test
        @DisplayName("타임존을 지정하여 변환한다")
        void convertStringToLocalDateTimeWithZone_success() {
            // given
            String dateTimeStr = "2024-01-15 10:30:00";
            String format = "yyyy-MM-dd HH:mm:ss";
            String zone = "Asia/Seoul";

            // when
            LocalDateTime result = DateUtils.convertStringToLocalDateTimeWithZone(dateTimeStr, format, zone);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getYear()).isEqualTo(2024);
        }

        @Test
        @DisplayName("UTC 타임존으로 변환한다")
        void convertStringToLocalDateTimeWithZone_utc() {
            // given
            String dateTimeStr = "2024-06-15 00:00:00";
            String format = "yyyy-MM-dd HH:mm:ss";
            String zone = "UTC";

            // when
            LocalDateTime result = DateUtils.convertStringToLocalDateTimeWithZone(dateTimeStr, format, zone);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getMonthValue()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("getKoreaDate 테스트")
    class GetKoreaDateTest {

        @Test
        @DisplayName("한국 시간 기준 Date를 반환한다")
        void getKoreaDate_returnsKoreaTime() {
            // when
            Date result = DateUtils.getKoreaDate();

            // then
            assertThat(result).isNotNull();
            assertThat(result).isBeforeOrEqualTo(new Date());
        }

        @Test
        @DisplayName("현재 시간과 큰 차이가 없다")
        void getKoreaDate_isCloseToNow() {
            // when
            Date result = DateUtils.getKoreaDate();
            Date now = new Date();

            // then
            long diff = Math.abs(result.getTime() - now.getTime());
            // 1분 이내의 차이
            assertThat(diff).isLessThan(60000);
        }
    }
}
