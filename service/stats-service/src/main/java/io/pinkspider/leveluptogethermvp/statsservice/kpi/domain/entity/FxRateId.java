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
public class FxRateId implements Serializable {

    /** 'YYYY-MM' */
    @Column(name = "rate_month", nullable = false, length = 7)
    private String rateMonth;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
}
