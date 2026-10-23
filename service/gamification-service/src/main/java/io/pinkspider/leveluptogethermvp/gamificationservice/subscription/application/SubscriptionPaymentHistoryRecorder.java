package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.ResolvedPrice;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionPaymentHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-486: 구독 결제 이력 적재 — 호출자(grant/웹훅) 트랜잭션에 참여한다.
 *
 * <p>호출자가 이미 "만료 엄격 연장" 조건으로 중복을 걸러주므로 여기서는 uk 키 존재 확인만 한 번 더 한다(verify·웹훅이 같은 결제를 각자 기록하려는 교차 케이스
 * 방어). 잔여 동시성 레이스는 uk_subscription_payment_dedup 이 막고, 그때의 예외는 호출자 트랜잭션 실패 → 스토어 웹훅 재시도로 수렴한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPaymentHistoryRecorder {

    private final SubscriptionPaymentHistoryRepository repository;
    private final SubscriptionPriceResolver priceResolver;

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void record(
            UserSubscription subscription,
            SubscriptionPaymentEventType eventType,
            boolean trial,
            BigDecimal priceAmount,
            String priceCurrency,
            String transactionId,
            LocalDateTime expiresAt,
            LocalDateTime occurredAt) {
        if (repository.existsByUserIdAndEventTypeAndExpiresAt(
                subscription.getUserId(), eventType, expiresAt)) {
            log.info(
                    "구독 결제 이력 중복 — 스킵: userId={}, event={}, expiresAt={}",
                    subscription.getUserId(),
                    eventType,
                    expiresAt);
            return;
        }
        // LUT-600: 스토어가 금액을 안 주면(Android) 플랜 정가로 채우고 출처를 남긴다
        ResolvedPrice price =
                priceResolver.resolve(
                        priceAmount,
                        priceCurrency,
                        trial,
                        subscription.getPlan(),
                        subscription.getPlatform(),
                        occurredAt == null ? null : occurredAt.toLocalDate());
        repository.save(
                SubscriptionPaymentHistory.builder()
                        .userId(subscription.getUserId())
                        .platform(subscription.getPlatform())
                        .productId(subscription.getProductId())
                        .basePlanId(subscription.getBasePlanId())
                        .plan(subscription.getPlan())
                        .eventType(eventType)
                        .trial(trial)
                        .priceAmount(price.amount())
                        .priceCurrency(price.currency())
                        .priceSource(price.source())
                        .transactionId(transactionId)
                        .expiresAt(expiresAt)
                        .occurredAt(occurredAt)
                        .build());
        log.info(
                "구독 결제 이력 기록: userId={}, event={}, plan={}, expiresAt={}, price={} {} ({})",
                subscription.getUserId(),
                eventType,
                subscription.getPlan(),
                expiresAt,
                price.amount(),
                price.currency(),
                price.source());
    }
}
