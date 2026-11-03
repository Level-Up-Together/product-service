package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RetentionMetric 테스트 (LUT-601)")
class RetentionMetricTest {

    private static final LocalDate COHORT = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("D_n 은 n일째 하루, W1 은 1~7일, W2_4 는 8~30일")
    void windows() {
        assertThat(RetentionMetric.D7.windowStart(COHORT)).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(RetentionMetric.D7.windowEnd(COHORT)).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(RetentionMetric.W1.windowStart(COHORT)).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(RetentionMetric.W1.windowEnd(COHORT)).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(RetentionMetric.W2_4.windowStart(COHORT)).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(RetentionMetric.W2_4.windowEnd(COHORT)).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("창의 마지막 날이 완성일 이하일 때만 닫힌 것으로 본다")
    void isClosed() {
        assertThat(RetentionMetric.D1.isClosed(COHORT, LocalDate.of(2026, 10, 2))).isTrue();
        assertThat(RetentionMetric.D1.isClosed(COHORT, LocalDate.of(2026, 10, 1))).isFalse();
        assertThat(RetentionMetric.D30.isClosed(COHORT, LocalDate.of(2026, 10, 31))).isTrue();
        assertThat(RetentionMetric.D30.isClosed(COHORT, LocalDate.of(2026, 10, 30))).isFalse();
    }
}
