package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KpiDates 테스트 (LUT-601)")
class KpiDatesTest {

    @Test
    @DisplayName("KST 날짜 → UTC 구간: 전날 15:00 ~ 당일 15:00")
    void utcRange() {
        LocalDate d = LocalDate.of(2026, 10, 23);
        assertThat(KpiDates.utcStart(d)).isEqualTo(LocalDateTime.of(2026, 10, 22, 15, 0));
        assertThat(KpiDates.utcEnd(d)).isEqualTo(LocalDateTime.of(2026, 10, 23, 15, 0));
    }

    @Test
    @DisplayName("월 구간과 월 키")
    void month() {
        YearMonth m = YearMonth.of(2026, 10);
        assertThat(KpiDates.utcMonthStart(m)).isEqualTo(LocalDateTime.of(2026, 9, 30, 15, 0));
        assertThat(KpiDates.utcMonthEnd(m)).isEqualTo(LocalDateTime.of(2026, 10, 31, 15, 0));
        assertThat(KpiDates.monthKey(m)).isEqualTo("2026-10");
        assertThat(KpiDates.today()).isNotNull();
    }
}
