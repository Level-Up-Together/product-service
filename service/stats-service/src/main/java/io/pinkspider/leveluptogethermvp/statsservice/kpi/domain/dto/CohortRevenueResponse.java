package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRevenue;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** LUT-601: 코호트 매출 1행 응답. ltv_krw = 누적 매출 ÷ cohort_size (분모 0 이면 null) */
public record CohortRevenueResponse(
        String cohortMonth,
        int monthN,
        int cohortSize,
        int payingUsers,
        BigDecimal cumulativeRevenueKrw,
        BigDecimal ltvKrw) {

    public static CohortRevenueResponse from(CohortRevenue r) {
        return new CohortRevenueResponse(
                r.getId().getCohortMonth(),
                r.getId().getMonthN(),
                r.getCohortSize(),
                r.getPayingUsers(),
                r.getCumulativeRevenueKrw(),
                ltv(r.getCumulativeRevenueKrw(), r.getCohortSize()));
    }

    static BigDecimal ltv(BigDecimal cumulative, int cohortSize) {
        if (cohortSize <= 0 || cumulative == null) {
            return null;
        }
        return cumulative.divide(BigDecimal.valueOf(cohortSize), 2, RoundingMode.HALF_UP);
    }
}
