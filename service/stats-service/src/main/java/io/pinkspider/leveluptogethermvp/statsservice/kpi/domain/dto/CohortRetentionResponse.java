package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto;

import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity.CohortRetention;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** LUT-601: 코호트 리텐션 1행 응답. rate 는 retained ÷ cohort_size (소수 4자리, 분모 0 이면 null) */
public record CohortRetentionResponse(
        LocalDate cohortDate,
        String metric,
        String source,
        int cohortSize,
        int retained,
        BigDecimal rate) {

    public static CohortRetentionResponse from(CohortRetention r) {
        return new CohortRetentionResponse(
                r.getId().getCohortDate(),
                r.getId().getMetric(),
                r.getId().getSource(),
                r.getCohortSize(),
                r.getRetained(),
                rate(r.getRetained(), r.getCohortSize()));
    }

    static BigDecimal rate(int retained, int cohortSize) {
        if (cohortSize <= 0) {
            return null;
        }
        return BigDecimal.valueOf(retained)
                .divide(BigDecimal.valueOf(cohortSize), 4, RoundingMode.HALF_UP);
    }
}
