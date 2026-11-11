package io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.policy;

import io.pinkspider.leveluptogethermvp.statsservice.api.exception.StatsInvalidParamException;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.KpiDates;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * LUT-790: 미션 통계 조회 기간 — KST 날짜 {@code start}~{@code end}(양끝 포함). {@code from > to} 는 바꿔서 받는다(KPI
 * 조회와 같은 관례). 한 번에 최대 {@value #MAX_DAYS}일이고 넘으면 400 {@code 100101} — 잘라서 다른 기간을 답하면 화면 숫자가 고른 기간과
 * 어긋난다.
 */
public record MissionStatsPeriod(LocalDate start, LocalDate end) {

    public static final int MAX_DAYS = 400;

    public static MissionStatsPeriod of(LocalDate from, LocalDate to) {
        LocalDate start = from.isAfter(to) ? to : from;
        LocalDate end = from.isAfter(to) ? from : to;
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new StatsInvalidParamException("to", "from 부터 " + MAX_DAYS + "일 이내");
        }
        return new MissionStatsPeriod(start, end);
    }

    /** 시작일 00:00 KST 의 UTC 시각 */
    public LocalDateTime utcFrom() {
        return KpiDates.utcStart(start);
    }

    /** 끝 날 다음 날 00:00 KST 의 UTC 시각(미포함) */
    public LocalDateTime utcTo() {
        return KpiDates.utcEnd(end);
    }

    /** 기간의 날짜 전부(오름차순) */
    public List<LocalDate> days() {
        return start.datesUntil(end.plusDays(1)).toList();
    }
}
