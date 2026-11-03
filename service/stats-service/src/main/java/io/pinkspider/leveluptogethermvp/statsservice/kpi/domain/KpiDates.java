package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * LUT-601: KPI 날짜 경계. 모든 지표의 "하루"는 <b>KST</b> 이고, 원천 테이블의 timestamp 는 UTC 저장이므로 KST 날짜 → UTC 구간으로
 * 바꿔 조회한다.
 *
 * <p>예) 2026-10-23(KST) = [2026-10-22T15:00, 2026-10-23T15:00) UTC.
 */
public final class KpiDates {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private KpiDates() {}

    public static LocalDate today() {
        return LocalDate.now(KST);
    }

    /** KST 날짜의 시작 순간을 UTC LocalDateTime 으로 */
    public static LocalDateTime utcStart(LocalDate kstDate) {
        return kstDate.atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** KST 날짜의 끝(다음 날 시작, exclusive) */
    public static LocalDateTime utcEnd(LocalDate kstDate) {
        return utcStart(kstDate.plusDays(1));
    }

    public static LocalDateTime utcMonthStart(YearMonth month) {
        return utcStart(month.atDay(1));
    }

    public static LocalDateTime utcMonthEnd(YearMonth month) {
        return utcStart(month.plusMonths(1).atDay(1));
    }

    /** 'YYYY-MM' 키 — cohort_revenue.cohort_month, fx_rate.rate_month */
    public static String monthKey(YearMonth month) {
        return month.toString();
    }
}
