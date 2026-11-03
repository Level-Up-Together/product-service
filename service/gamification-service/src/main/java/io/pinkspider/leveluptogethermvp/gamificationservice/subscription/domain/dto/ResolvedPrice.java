package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.PriceSource;
import java.math.BigDecimal;

/**
 * LUT-600: 결제 이력에 적을 금액·통화·출처. 출처가 null 이면 금액도 null(미상).
 *
 * @param amount 금액 (TRIAL 은 0)
 * @param currency ISO 4217 — 금액이 없어도 스토어가 준 통화는 보존
 * @param source 출처 — STORE / LIST / TRIAL / null(미상)
 */
public record ResolvedPrice(BigDecimal amount, String currency, PriceSource source) {

    public static ResolvedPrice unknown(String currency) {
        return new ResolvedPrice(null, currency, null);
    }
}
