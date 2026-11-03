package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/** LUT-601: 가입 월 코호트의 month_n 종료 시점 누적 KRW 매출 — LTV 곡선의 점 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cohort_revenue")
@Comment("가입 월 코호트 누적 매출 (LUT-601)")
public class CohortRevenue extends LocalDateTimeBaseEntity {

    @EmbeddedId private CohortRevenueId id;

    @Column(name = "cohort_size", nullable = false)
    private Integer cohortSize;

    @Column(name = "paying_users", nullable = false)
    private Integer payingUsers;

    @Column(name = "cumulative_revenue_krw", nullable = false, precision = 14, scale = 2)
    private BigDecimal cumulativeRevenueKrw;

    public static CohortRevenue create(
            String cohortMonth,
            int monthN,
            int cohortSize,
            int payingUsers,
            BigDecimal cumulativeRevenueKrw) {
        CohortRevenue r = new CohortRevenue();
        r.id = new CohortRevenueId(cohortMonth, monthN);
        r.update(cohortSize, payingUsers, cumulativeRevenueKrw);
        return r;
    }

    public void update(int cohortSize, int payingUsers, BigDecimal cumulativeRevenueKrw) {
        this.cohortSize = cohortSize;
        this.payingUsers = payingUsers;
        this.cumulativeRevenueKrw = cumulativeRevenueKrw;
    }
}
