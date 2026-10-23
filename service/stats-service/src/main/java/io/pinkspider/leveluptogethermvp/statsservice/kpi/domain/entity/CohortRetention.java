package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionMetric;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionSource;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/** LUT-601: 가입 코호트 × 지표 × 원천 1행. retained ÷ cohort_size 가 리텐션율. 창이 닫힌 지표만 저장된다. */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cohort_retention")
@Comment("가입 코호트 리텐션 (LUT-601)")
public class CohortRetention extends LocalDateTimeBaseEntity {

    @EmbeddedId private CohortRetentionId id;

    @Column(name = "cohort_size", nullable = false)
    private Integer cohortSize;

    @Column(name = "retained", nullable = false)
    private Integer retained;

    public static CohortRetention create(
            LocalDate cohortDate,
            RetentionMetric metric,
            RetentionSource source,
            int cohortSize,
            int retained) {
        CohortRetention r = new CohortRetention();
        r.id = new CohortRetentionId(cohortDate, metric.name(), source.name());
        r.cohortSize = cohortSize;
        r.retained = retained;
        return r;
    }

    public void update(int cohortSize, int retained) {
        this.cohortSize = cohortSize;
        this.retained = retained;
    }
}
