package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums;

import java.time.LocalDate;

/**
 * LUT-601: 코호트 리텐션 지표. D_n 은 가입일 기준 정확히 n일째(classic), W1/W2_4 는 창 안에 1회 이상 활동(window).
 *
 * <p>가입 당일(D0)은 쓰지 않는다 — LUT-597 이전 출석은 규칙상 0 이었고, 요청 기반 활동은 가입 호출 자체가 포함돼 의미가 없다.
 */
public enum RetentionMetric {
    D1(1, 1),
    D3(3, 3),
    D7(7, 7),
    D14(14, 14),
    D30(30, 30),
    W1(1, 7),
    W2_4(8, 30);

    private final int fromOffsetDays;
    private final int toOffsetDays;

    RetentionMetric(int fromOffsetDays, int toOffsetDays) {
        this.fromOffsetDays = fromOffsetDays;
        this.toOffsetDays = toOffsetDays;
    }

    public LocalDate windowStart(LocalDate cohortDate) {
        return cohortDate.plusDays(fromOffsetDays);
    }

    public LocalDate windowEnd(LocalDate cohortDate) {
        return cohortDate.plusDays(toOffsetDays);
    }

    /** 창의 마지막 날까지 데이터가 완성됐는가 (lastCompleteDate = 보통 어제) */
    public boolean isClosed(LocalDate cohortDate, LocalDate lastCompleteDate) {
        return !windowEnd(cohortDate).isAfter(lastCompleteDate);
    }

    public static final int MAX_OFFSET_DAYS = 30;
}
