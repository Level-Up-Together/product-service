package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.ResolvedPrice;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.PlanListPrice;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.PriceSource;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.PlanListPriceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionPriceResolver 테스트 (LUT-600)")
class SubscriptionPriceResolverTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 23);

    @Mock private PlanListPriceRepository repository;

    @InjectMocks private SubscriptionPriceResolver resolver;

    private PlanListPrice listPrice(String currency, String amount) {
        return PlanListPrice.of(
                SubscriptionPlan.MONTHLY,
                "android",
                currency,
                new BigDecimal(amount),
                LocalDate.of(2026, 1, 1));
    }

    private void stubListPrice(String currency, Optional<PlanListPrice> result) {
        when(repository
                        .findTopByPlanAndPlatformAndCurrencyAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                SubscriptionPlan.MONTHLY, "android", currency, DATE))
                .thenReturn(result);
    }

    @Test
    @DisplayName("스토어가 금액을 줬으면 그대로 STORE — 정가 조회 안 함")
    void storePriceWins() {
        ResolvedPrice r =
                resolver.resolve(
                        new BigDecimal("4900.00"),
                        "KRW",
                        false,
                        SubscriptionPlan.MONTHLY,
                        "ios",
                        DATE);

        assertThat(r.amount()).isEqualByComparingTo("4900.00");
        assertThat(r.currency()).isEqualTo("KRW");
        assertThat(r.source()).isEqualTo(PriceSource.STORE);
        verify(repository, never())
                .findTopByPlanAndPlatformAndCurrencyAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        any(), any(), any(), any());
    }

    @Test
    @DisplayName("스토어 금액 없음 + 정가 있음 → 정가 금액·통화, LIST (플랫폼은 소문자로 조회)")
    void listPriceFallback() {
        stubListPrice("KRW", Optional.of(listPrice("KRW", "4900.00")));

        ResolvedPrice r =
                resolver.resolve(null, null, false, SubscriptionPlan.MONTHLY, "ANDROID", DATE);

        assertThat(r.amount()).isEqualByComparingTo("4900.00");
        assertThat(r.currency()).isEqualTo("KRW");
        assertThat(r.source()).isEqualTo(PriceSource.LIST);
    }

    @Test
    @DisplayName("체험 결제는 0원 TRIAL — 통화는 스토어 값 우선")
    void trialWithStoreCurrency() {
        stubListPrice("USD", Optional.empty());

        ResolvedPrice r =
                resolver.resolve(null, "USD", true, SubscriptionPlan.MONTHLY, "android", DATE);

        assertThat(r.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.currency()).isEqualTo("USD");
        assertThat(r.source()).isEqualTo(PriceSource.TRIAL);
    }

    @Test
    @DisplayName("체험 결제, 스토어 통화 없음 → 정가 통화, 정가도 없으면 KRW")
    void trialCurrencyFallbacks() {
        stubListPrice("KRW", Optional.of(listPrice("KRW", "4900.00")));
        ResolvedPrice withList =
                resolver.resolve(null, null, true, SubscriptionPlan.MONTHLY, "android", DATE);
        assertThat(withList.currency()).isEqualTo("KRW");
        assertThat(withList.source()).isEqualTo(PriceSource.TRIAL);

        stubListPrice("KRW", Optional.empty());
        ResolvedPrice noList =
                resolver.resolve(null, null, true, SubscriptionPlan.MONTHLY, "android", DATE);
        assertThat(noList.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(noList.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("스토어 금액도 정가도 없으면 금액·출처 null (통화는 스토어 값 보존)")
    void unknownWhenNothing() {
        stubListPrice("USD", Optional.empty());

        ResolvedPrice r =
                resolver.resolve(null, "USD", false, SubscriptionPlan.MONTHLY, "android", DATE);

        assertThat(r.amount()).isNull();
        assertThat(r.source()).isNull();
        assertThat(r.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("플랜·플랫폼·날짜가 없으면 정가를 조회하지 않고 미상")
    void skipsLookupWhenKeyMissing() {
        assertThat(resolver.resolve(null, null, false, null, "android", DATE).source()).isNull();
        assertThat(
                        resolver.resolve(null, null, false, SubscriptionPlan.MONTHLY, null, DATE)
                                .source())
                .isNull();
        assertThat(
                        resolver.resolve(
                                        null,
                                        null,
                                        false,
                                        SubscriptionPlan.MONTHLY,
                                        "android",
                                        null)
                                .source())
                .isNull();
        verify(repository, never())
                .findTopByPlanAndPlatformAndCurrencyAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        any(), any(), any(), any());
    }
}
