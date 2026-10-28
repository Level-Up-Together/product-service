package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
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
public class CohortRetentionId implements Serializable {

    @Column(name = "cohort_date", nullable = false)
    private LocalDate cohortDate;

    /** RetentionMetric 이름 */
    @Column(name = "metric", nullable = false, length = 10)
    private String metric;

    /** RetentionSource 이름 */
    @Column(name = "source", nullable = false, length = 12)
    private String source;
}
