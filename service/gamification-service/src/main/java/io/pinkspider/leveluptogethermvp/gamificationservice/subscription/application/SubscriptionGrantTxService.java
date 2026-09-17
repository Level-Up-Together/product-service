package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.SubscriptionAccountToken;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerificationResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.UserSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-451: 검증 완료된 구독의 권한 기록(upsert) — 한 트랜잭션.
 *
 * <p>멱등 규칙: 같은 검증 결과의 재전송(만료가 기존보다 늦지 않음)은 행을 바꾸지 않는다. 스토어에서
 * 갓 갱신된(더 늦은 만료) 결과만 반영해, 오래된 트랜잭션의 Restore 재전송이 상태를 되감지 못하게 한다.
 * (trial_used 는 예외 — 한 번 true 면 유지·승격만 한다)
 *
 * <p>LUT-507 소유권 정책: 스토어 구독(originalTransactionId·purchaseToken)은 <b>한 번에 한 앱 계정만</b> 쓰고,
 * 주인은 <b>마지막으로 결제한 계정</b>이다. 거래에 앱 계정 토큰이 있으면 요청 유저와 대조하고, 다른 계정이 보유 중인
 * 구독은 그 계정이 아직 권한이 있으면 차단(120802), 만료됐고 더 늦은 만료의 새 결제면 결제한 계정으로 이전한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionGrantTxService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPaymentHistoryRecorder paymentHistoryRecorder;

    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserSubscription upsert(
            String userId,
            SubscriptionPlan plan,
            String platform,
            SubscriptionVerificationResult result,
            LocalDateTime expiresAt,
            LocalDateTime now) {
        resolveOwnership(userId, result, expiresAt, now);

        UserSubscription subscription =
                userSubscriptionRepository.findByUserId(userId).orElse(null);
        if (subscription == null) {
            UserSubscription created =
                    userSubscriptionRepository.saveAndFlush(
                            UserSubscription.builder()
                                    .userId(userId)
                                    .platform(platform)
                                    .productId(result.storeProductId())
                                    .basePlanId(result.basePlanId())
                                    .plan(plan)
                                    .startedAt(
                                            result.startedAt() != null ? result.startedAt() : now)
                                    .expiresAt(expiresAt)
                                    .autoRenew(result.autoRenew())
                                    .trialUsed(result.trial())
                                    .originalTransactionId(result.originalTransactionId())
                                    .purchaseToken(result.purchaseToken())
                                    .build());
            // LUT-486: 최초 구매(행 신설) = PURCHASE 결제 이력
            paymentHistoryRecorder.record(
                    created,
                    SubscriptionPaymentEventType.PURCHASE,
                    result.trial(),
                    result.priceAmount(),
                    result.priceCurrency(),
                    result.transactionId(),
                    expiresAt,
                    now);
            return created;
        }

        if (result.trial()) {
            subscription.setTrialUsed(true);
        }
        if (!expiresAt.isAfter(subscription.getExpiresAt())) {
            // 기존보다 늦지 않은 만료 = 같은 트랜잭션 재전송이거나 오래된 Restore — 상태 되감기 방지
            log.info(
                    "구독 멱등 재전송 — 변경 없음: userId={}, 기존만료={}, 요청만료={}",
                    userId,
                    subscription.getExpiresAt(),
                    expiresAt);
            return subscription;
        }

        subscription.setPlatform(platform);
        subscription.setProductId(result.storeProductId());
        subscription.setBasePlanId(result.basePlanId());
        subscription.setPlan(plan);
        subscription.renew(expiresAt);
        subscription.setAutoRenew(result.autoRenew());
        if (result.originalTransactionId() != null) {
            subscription.setOriginalTransactionId(result.originalTransactionId());
        }
        if (result.purchaseToken() != null) {
            subscription.setPurchaseToken(result.purchaseToken());
        }
        // LUT-486: 만료 엄격 연장 = 갱신 결제 이력 (웹훅과 이중 도착해도 늦은 쪽은 위 멱등 가드에서 걸러짐)
        paymentHistoryRecorder.record(
                subscription,
                SubscriptionPaymentEventType.RENEWAL,
                result.trial(),
                result.priceAmount(),
                result.priceCurrency(),
                result.transactionId(),
                expiresAt,
                now);
        return subscription;
    }

    /**
     * LUT-507: 소유권 판정. 순서 — ① 거래의 앱 계정 토큰이 요청 유저의 것이 아니면 차단(다른 계정이 결제한 거래의
     * 복원·재전달) ② 같은 스토어 구독을 다른 계정이 보유 중이면: 그 계정이 아직 권한이 있거나(활성·유예) 이 결과가 더 늦은
     * 만료의 새 결제가 아니면 차단, 그 외(만료 후 재구독)는 옛 주인에게서 스토어 키를 떼어 요청 유저에게 이전한다.
     *
     * <p>토큰 없는 예전 거래는 ①을 건너뛰고 ②(스토어 키 소유자)로만 판단한다.
     */
    private void resolveOwnership(
            String userId,
            SubscriptionVerificationResult result,
            LocalDateTime expiresAt,
            LocalDateTime now) {
        if (result.appAccountToken() != null
                && !SubscriptionAccountToken.matches(result.appAccountToken(), userId)) {
            log.warn(
                    "구독 거래 앱 계정 토큰 불일치 — 다른 계정이 결제한 거래: 요청 userId={}, token={}",
                    userId,
                    result.appAccountToken());
            throw new CustomException("120802", "error.subscription.transaction_already_used");
        }

        Optional<UserSubscription> existing = findByStoreKeys(result);
        if (existing.isEmpty() || existing.get().getUserId().equals(userId)) {
            return;
        }

        UserSubscription previousOwner = existing.get();
        boolean previousOwnerEntitled = previousOwner.isEntitled(now);
        boolean newerPayment = expiresAt.isAfter(previousOwner.getExpiresAt());
        if (previousOwnerEntitled || !newerPayment) {
            log.warn(
                    "구독 트랜잭션 교차 계정 재사용 차단: 요청 userId={}, 보유 userId={}, 보유권한={}, 새결제={}",
                    userId,
                    previousOwner.getUserId(),
                    previousOwnerEntitled,
                    newerPayment);
            throw new CustomException("120802", "error.subscription.transaction_already_used");
        }

        // 만료된 옛 주인 → 결제한 계정으로 이전. 스토어 키 유니크 제약 때문에 옛 행에서 먼저 떼고 flush 한다.
        log.info(
                "구독 소유권 이전: 옛 userId={} (만료 {}) → 새 userId={}, 새 만료={}",
                previousOwner.getUserId(),
                previousOwner.getExpiresAt(),
                userId,
                expiresAt);
        previousOwner.setOriginalTransactionId(null);
        previousOwner.setPurchaseToken(null);
        previousOwner.setAutoRenew(false);
        userSubscriptionRepository.saveAndFlush(previousOwner);
    }

    /** 스토어 키(iOS originalTransactionId / Android purchaseToken·linkedPurchaseToken)로 보유 행 조회 */
    private Optional<UserSubscription> findByStoreKeys(SubscriptionVerificationResult result) {
        if (result.originalTransactionId() != null) {
            return userSubscriptionRepository.findByOriginalTransactionId(
                    result.originalTransactionId());
        }
        if (result.purchaseToken() != null) {
            Optional<UserSubscription> existing =
                    userSubscriptionRepository.findByPurchaseToken(result.purchaseToken());
            if (existing.isEmpty() && result.linkedPurchaseToken() != null) {
                // LUT-499: 재구독으로 새 토큰을 받아도 옛 토큰(linkedPurchaseToken) 소유자를 본다
                return userSubscriptionRepository.findByPurchaseToken(result.linkedPurchaseToken());
            }
            return existing;
        }
        return Optional.empty();
    }
}
