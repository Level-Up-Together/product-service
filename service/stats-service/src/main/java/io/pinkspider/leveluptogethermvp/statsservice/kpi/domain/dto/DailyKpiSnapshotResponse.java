package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.DailyKpiSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;

/** LUT-601: 일별 KPI 1행 응답 (어드민 Feign 패스스루 대상). revenue_by_currency 는 JSON 문자열 원본 */
public record DailyKpiSnapshotResponse(
        LocalDate snapshotDate,
        int signups,
        int withdrawals,
        int activeUsers,
        int dau,
        int wau,
        int mau,
        int dauWeb,
        int dauIos,
        int dauAndroid,
        int attendanceDau,
        int missionCompletions,
        int missionActiveUsers,
        int newSubscriptions,
        int activeSubscriptions,
        int churnedSubscriptions,
        int trialStarts,
        int trialConversions,
        int payingUsers,
        BigDecimal revenueKrw,
        String revenueByCurrency) {

    public static DailyKpiSnapshotResponse from(DailyKpiSnapshot s) {
        return new DailyKpiSnapshotResponse(
                s.getSnapshotDate(),
                s.getSignups(),
                s.getWithdrawals(),
                s.getActiveUsers(),
                s.getDau(),
                s.getWau(),
                s.getMau(),
                s.getDauWeb(),
                s.getDauIos(),
                s.getDauAndroid(),
                s.getAttendanceDau(),
                s.getMissionCompletions(),
                s.getMissionActiveUsers(),
                s.getNewSubscriptions(),
                s.getActiveSubscriptions(),
                s.getChurnedSubscriptions(),
                s.getTrialStarts(),
                s.getTrialConversions(),
                s.getPayingUsers(),
                s.getRevenueKrw(),
                s.getRevenueByCurrency());
    }
}
