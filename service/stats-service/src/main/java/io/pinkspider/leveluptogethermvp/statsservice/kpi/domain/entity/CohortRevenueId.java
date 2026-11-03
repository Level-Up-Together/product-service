package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CohortRevenueId implements Serializable {

    /** 'YYYY-MM' */
    @Column(name = "cohort_month", nullable = false, length = 7)
    private String cohortMonth;

    /** 가입 월 이후 n개월 (0,1,3,6,12) */
    @Column(name = "month_n", nullable = false)
    private Integer monthN;
}
