package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto;

import java.math.BigDecimal;

/** LUT-601: 하루치 KPI 계산 결과 — 엔티티 생성/갱신에 쓴다 */
public record DailyKpiValues(
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
        String revenueByCurrencyJson) {}
