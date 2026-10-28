package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.ResolvedPrice;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.PlanListPrice;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.PriceSource;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.PlanListPriceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LUT-600: 결제 이력 금액 결정.
 *
 * <ol>
 *   <li>스토어가 금액을 줬으면 그대로 — {@code STORE}
 *   <li>무료 체험 결제면 0 — {@code TRIAL} (통화는 스토어 값 → 정가 통화 → KRW 순)
 *   <li>아니면 플랜 정가({@code plan_list_price}, 결제일 이전 최신) — {@code LIST}
 *   <li>정가도 없으면 금액 null, 출처 null (warn 로그) — 나중에 정가를 넣고 백필
 * </ol>
 *
 * <p>캐시를 두지 않는다 — 결제 이벤트는 하루 몇 건이고 조회는 유니크 인덱스 1회다 (무기한 캐시 사고 LUT-534 회피).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SubscriptionPriceResolver {

    static final String DEFAULT_CURRENCY = "KRW";

    private final PlanListPriceRepository planListPriceRepository;

    public ResolvedPrice resolve(
            BigDecimal storeAmount,
            String storeCurrency,
            boolean trial,
            SubscriptionPlan plan,
            String platform,
            LocalDate occurredOn) {
        if (storeAmount != null) {
            return new ResolvedPrice(storeAmount, storeCurrency, PriceSource.STORE);
        }
        String currency = storeCurrency != null ? storeCurrency : DEFAULT_CURRENCY;
        Optional<PlanListPrice> listPrice = findListPrice(plan, platform, currency, occurredOn);
        if (trial) {
            String trialCurrency =
                    storeCurrency != null
                            ? storeCurrency
                            : listPrice.map(PlanListPrice::getCurrency).orElse(DEFAULT_CURRENCY);
            return new ResolvedPrice(BigDecimal.ZERO, trialCurrency, PriceSource.TRIAL);
        }
        if (listPrice.isPresent()) {
            PlanListPrice price = listPrice.get();
            return new ResolvedPrice(price.getAmount(), price.getCurrency(), PriceSource.LIST);
        }
        log.warn(
                "구독 결제 금액 미상 — 스토어 금액 없고 정가도 없음: plan={}, platform={}, currency={}, date={}",
                plan,
                platform,
                currency,
                occurredOn);
        return ResolvedPrice.unknown(storeCurrency);
    }

    private Optional<PlanListPrice> findListPrice(
            SubscriptionPlan plan, String platform, String currency, LocalDate occurredOn) {
        if (plan == null || platform == null || occurredOn == null) {
            return Optional.empty();
        }
        return planListPriceRepository
                .findTopByPlanAndPlatformAndCurrencyAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        plan, platform.toLowerCase(Locale.ROOT), currency, occurredOn);
    }
}
