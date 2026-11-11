package io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.policy;

/** LUT-790: 미션 통계의 비율·평균 — 소수 한 자리, 분모가 0 이면 null(화면 "–") */
public final class MissionStatsRates {

    private MissionStatsRates() {}

    /** part ÷ whole × 100 (%) */
    public static Double percent(long part, long whole) {
        if (whole <= 0) {
            return null;
        }
        return Math.round(part * 1000.0 / whole) / 10.0;
    }

    /** total ÷ count */
    public static Double average(long total, long count) {
        if (count <= 0) {
            return null;
        }
        return Math.round(total * 10.0 / count) / 10.0;
    }
}
