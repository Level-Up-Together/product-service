package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleOrder;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionPaymentHistoryRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LUT-663: 환불 원장 행에 넣을 금액. 예전엔 세 환불 경로(ASSN REFUND/REVOKE·자가 치유·RTDN)가 전부 null 을 넘겨 기록기가 KRW
 * 정가(LIST)로 채웠다 — 다른 통화 결제를 환불하면 KPI 매출에서 원화 정가가 빠졌다.
 *
 * <p>우선순위: ① 스토어가 준 실제 금액(iOS 환불 알림 거래의 price/currency, Android 는 voided 주문 ID 로 Orders API total)
 * → ② 같은 거래/주문 ID 의 원 결제 행 금액 → ③ 그 유저의 최신 결제 행 금액 → ④ 없으면 null(기록기가 정가로 채움, 기존 동작). Apple·Google
 * 모두 구독 환불은 전액이 기본이라 원 결제 금액 = 환불액로 본다(부분 환불 금액은 두 스토어 알림에 없다).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionRefundAmountResolver {

    private final SubscriptionPaymentHistoryRepository historyRepository;
    private final SubscriptionVerificationService verificationService;
    private final RtdnMetrics metrics;

    /** 환불액 — amount 가 null 이면 모름(기록기가 정가로 채운다) */
    public record RefundAmount(BigDecimal amount, String currency) {
        public static final RefundAmount UNKNOWN = new RefundAmount(null, null);

        public boolean known() {
            return amount != null;
        }
    }

    /** iOS — 환불 알림/스냅샷의 거래 price(밀리유닛)·currency. 없으면 같은 transactionId 의 원 결제 행. */
    public RefundAmount forApple(
            UserSubscription subscription, JWSTransactionDecodedPayload transaction) {
        if (transaction != null) {
            BigDecimal price =
                    SubscriptionVerificationService.applePriceToDecimal(transaction.getPrice());
            if (price != null && transaction.getCurrency() != null) {
                return new RefundAmount(price, transaction.getCurrency());
            }
        }
        return fromOriginalPayment(
                subscription.getUserId(),
                transaction == null ? null : transaction.getTransactionId());
    }

    /**
     * Android — RTDN voided 의 주문 ID 로 orders.get(total). 조회 실패·REVOKED(12, 주문 ID 없음)는 같은 주문 ID 의 원
     * 결제 행 → 최신 결제 행. 주문 조회는 best-effort — 실패해도 권한 종료를 막지 않는다.
     */
    public RefundAmount forGoogle(UserSubscription subscription, String orderId) {
        if (orderId != null && !orderId.isBlank()) {
            try {
                Optional<GoogleOrder> order = verificationService.fetchGoogleOrder(orderId);
                if (order.isPresent() && order.get().totalAmount() != null) {
                    return new RefundAmount(order.get().totalAmount(), order.get().totalCurrency());
                }
                if (order.isEmpty()) {
                    metrics.orderLookupFailed("not_found");
                }
            } catch (StoreTransientException e) {
                metrics.orderLookupFailed("transient");
                log.warn(
                        "환불 주문 조회 일시 실패 — 원 결제 행 금액으로: orderId={}, error={}",
                        orderId,
                        e.getMessage());
            } catch (CustomException e) {
                metrics.orderLookupFailed("rejected");
                log.warn("환불 주문 조회 거부 — 원 결제 행 금액으로: orderId={}, code={}", orderId, e.getCode());
            }
        }
        return fromOriginalPayment(subscription.getUserId(), orderId);
    }

    private RefundAmount fromOriginalPayment(String userId, String transactionId) {
        Optional<SubscriptionPaymentHistory> row = Optional.empty();
        if (transactionId != null && !transactionId.isBlank()) {
            row =
                    historyRepository
                            .findFirstByUserIdAndTransactionIdAndEventTypeNotAndPriceAmountIsNotNullOrderByOccurredAtDesc(
                                    userId, transactionId, SubscriptionPaymentEventType.REFUND);
        }
        if (row.isEmpty()) {
            row =
                    historyRepository
                            .findFirstByUserIdAndEventTypeNotAndPriceAmountIsNotNullOrderByOccurredAtDesc(
                                    userId, SubscriptionPaymentEventType.REFUND);
        }
        if (row.isEmpty()) {
            log.info("환불액 원천 없음 — 기록기가 정가로 채움: userId={}, transactionId={}", userId, transactionId);
            return RefundAmount.UNKNOWN;
        }
        return new RefundAmount(row.get().getPriceAmount(), row.get().getPriceCurrency());
    }
}
