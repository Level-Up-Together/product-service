package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application.SubscriptionRefundAmountResolver.RefundAmount;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleOrder;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionPaymentHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionRefundAmountResolver 테스트 (LUT-663)")
class SubscriptionRefundAmountResolverTest {

    @Mock private SubscriptionPaymentHistoryRepository historyRepository;
    @Mock private SubscriptionVerificationService verificationService;
    @Mock private RtdnMetrics metrics;

    @InjectMocks private SubscriptionRefundAmountResolver resolver;

    private static final UserSubscription SUB =
            UserSubscription.builder().userId("user-1").platform("android").build();

    private static SubscriptionPaymentHistory paidRow(String amount, String currency) {
        return SubscriptionPaymentHistory.builder()
                .userId("user-1")
                .eventType(SubscriptionPaymentEventType.PURCHASE)
                .priceAmount(new BigDecimal(amount))
                .priceCurrency(currency)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("iOS")
    class AppleTest {

        @Test
        @DisplayName("환불 알림 거래에 price/currency 가 있으면 그 값(밀리유닛 → 금액)을 쓴다")
        void usesTransactionPrice() {
            JWSTransactionDecodedPayload tx =
                    new JWSTransactionDecodedPayload()
                            .transactionId("tx-1")
                            .price(4990L)
                            .currency("USD");

            RefundAmount r = resolver.forApple(SUB, tx);

            assertThat(r.amount()).isEqualByComparingTo("4.99");
            assertThat(r.currency()).isEqualTo("USD");
            verify(historyRepository, never())
                    .findFirstByUserIdAndTransactionIdAndEventTypeNotAndPriceAmountIsNotNullOrderByOccurredAtDesc(
                            anyString(), anyString(), any());
        }

        @Test
        @DisplayName("거래에 가격이 없으면 같은 transactionId 의 원 결제 행 금액을 쓴다")
        void fallsBackToOriginalPaymentRow() {
            JWSTransactionDecodedPayload tx =
                    new JWSTransactionDecodedPayload().transactionId("tx-1");
            when(historyRepository
                            .findFirstByUserIdAndTransactionIdAndEventTypeNotAndPriceAmountIsNotNullOrderByOccurredAtDesc(
                                    "user-1", "tx-1", SubscriptionPaymentEventType.REFUND))
                    .thenReturn(Optional.of(paidRow("4900", "KRW")));

            RefundAmount r = resolver.forApple(SUB, tx);

            assertThat(r.amount()).isEqualByComparingTo("4900");
            assertThat(r.currency()).isEqualTo("KRW");
        }

        @Test
        @DisplayName("원 결제 행도 없으면 최신 결제 행, 그것도 없으면 UNKNOWN(기록기가 정가로)")
        void fallsBackToLatestThenUnknown() {
            JWSTransactionDecodedPayload tx =
                    new JWSTransactionDecodedPayload().transactionId("tx-1");
            when(historyRepository
                            .findFirstByUserIdAndTransactionIdAndEventTypeNotAndPriceAmountIsNotNullOrderByOccurredAtDesc(
                                    "user-1", "tx-1", SubscriptionPaymentEventType.REFUND))
                    .thenReturn(Optional.empty());
            when(historyRepository
                            .findFirstByUserIdAndEventTypeNotAndPriceAmountIsNotNullOrderByOccurredAtDesc(
                                    "user-1", SubscriptionPaymentEventType.REFUND))
                    .thenReturn(Optional.of(paidRow("44000", "KRW")), Optional.empty());

            assertThat(resolver.forApple(SUB, tx).amount()).isEqualByComparingTo("44000");
            RefundAmount none = resolver.forApple(SUB, tx);
            assertThat(none.known()).isFalse();
            assertThat(none.currency()).isNull();
        }
    }

    @Nested
    @DisplayName("Android")
    class GoogleTest {

        @Test
        @DisplayName("voided 주문 ID 로 Orders API total 을 쓴다")
        void usesOrderTotal() {
            when(verificationService.fetchGoogleOrder("GPA.1-2"))
                    .thenReturn(
                            Optional.of(
                                    new GoogleOrder(
                                            "GPA.1-2",
                                            "CANCELED",
                                            LocalDateTime.now(),
                                            new BigDecimal("4900"),
                                            "KRW",
                                            null,
                                            null)));

            RefundAmount r = resolver.forGoogle(SUB, "GPA.1-2");

            assertThat(r.amount()).isEqualByComparingTo("4900");
            assertThat(r.currency()).isEqualTo("KRW");
        }

        @Test
        @DisplayName("주문 조회 일시 실패는 카운터만 남기고 같은 주문 ID 의 원 결제 행으로 폴백한다 — 권한 종료를 막지 않는다")
        void transientFailureFallsBack() {
            when(verificationService.fetchGoogleOrder("GPA.1-2"))
                    .thenThrow(new StoreTransientException("timeout", new RuntimeException()));
            when(historyRepository
                            .findFirstByUserIdAndTransactionIdAndEventTypeNotAndPriceAmountIsNotNullOrderByOccurredAtDesc(
                                    "user-1", "GPA.1-2", SubscriptionPaymentEventType.REFUND))
                    .thenReturn(Optional.of(paidRow("4.99", "USD")));

            RefundAmount r = resolver.forGoogle(SUB, "GPA.1-2");

            assertThat(r.amount()).isEqualByComparingTo("4.99");
            assertThat(r.currency()).isEqualTo("USD");
            verify(metrics).orderLookupFailed("transient");
        }

        @Test
        @DisplayName("주문이 없거나(404) 거부되면 폴백, REVOKED(주문 ID 없음)는 조회 없이 최신 결제 행")
        void notFoundRejectedAndNoOrderId() {
            when(verificationService.fetchGoogleOrder("GPA.404")).thenReturn(Optional.empty());
            when(verificationService.fetchGoogleOrder("GPA.403"))
                    .thenThrow(new CustomException("120702", "error.iap.verification_failed"));
            when(historyRepository
                            .findFirstByUserIdAndTransactionIdAndEventTypeNotAndPriceAmountIsNotNullOrderByOccurredAtDesc(
                                    eq("user-1"),
                                    anyString(),
                                    eq(SubscriptionPaymentEventType.REFUND)))
                    .thenReturn(Optional.empty());
            when(historyRepository
                            .findFirstByUserIdAndEventTypeNotAndPriceAmountIsNotNullOrderByOccurredAtDesc(
                                    "user-1", SubscriptionPaymentEventType.REFUND))
                    .thenReturn(Optional.of(paidRow("4900", "KRW")));

            assertThat(resolver.forGoogle(SUB, "GPA.404").amount()).isEqualByComparingTo("4900");
            assertThat(resolver.forGoogle(SUB, "GPA.403").amount()).isEqualByComparingTo("4900");
            assertThat(resolver.forGoogle(SUB, null).amount()).isEqualByComparingTo("4900");
            verify(metrics).orderLookupFailed("not_found");
            verify(metrics).orderLookupFailed("rejected");
            verify(verificationService, never()).fetchGoogleOrder(null);
        }
    }
}
