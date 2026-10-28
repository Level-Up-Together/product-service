package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-601: 월별 환율(1 단위 외화 = krw_per_unit 원). 수동 입력. 없는 통화는 revenue_krw 에서 빠지고 revenue_by_currency 에만
 * 남는다
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "fx_rate")
@Comment("월별 환율 (LUT-601, 수동)")
public class FxRate {

    @EmbeddedId private FxRateId id;

    @Column(name = "krw_per_unit", nullable = false, precision = 14, scale = 6)
    private BigDecimal krwPerUnit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static FxRate of(String rateMonth, String currency, BigDecimal krwPerUnit) {
        FxRate rate = new FxRate();
        rate.id = new FxRateId(rateMonth, currency);
        rate.krwPerUnit = krwPerUnit;
        rate.createdAt = LocalDateTime.now();
        return rate;
    }
}
