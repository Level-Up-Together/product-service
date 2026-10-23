package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * LUT-601: 매출 집계 — 통화별 합(환불은 음수로 반영됨)과 결제 유저 집합. 원천(구독·다이아)별 결과를 {@link #merge} 로 합친다.
 *
 * @param byCurrency 통화 → 금액 합
 * @param payingUserIds 금액 > 0 인 결제를 한 유저 (환불 제외)
 */
public record RevenueAggregate(Map<String, BigDecimal> byCurrency, Set<String> payingUserIds) {

    public static RevenueAggregate empty() {
        return new RevenueAggregate(new HashMap<>(), new HashSet<>());
    }

    public RevenueAggregate merge(RevenueAggregate other) {
        Map<String, BigDecimal> merged = new HashMap<>(byCurrency);
        other.byCurrency.forEach(
                (currency, amount) -> merged.merge(currency, amount, BigDecimal::add));
        Set<String> users = new HashSet<>(payingUserIds);
        users.addAll(other.payingUserIds);
        return new RevenueAggregate(merged, users);
    }

    public BigDecimal amountOf(String currency) {
        return byCurrency.getOrDefault(currency, BigDecimal.ZERO);
    }
}
