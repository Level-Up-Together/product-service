package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerificationResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionGrantTxService {

    private final UserSubscriptionRepository userSubscriptionRepository;

    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserSubscription upsert(
            String userId,
            SubscriptionPlan plan,
            String platform,
            SubscriptionVerificationResult result,
            LocalDateTime expiresAt,
            LocalDateTime now) {
        guardCrossUserReuse(userId, result);

        UserSubscription subscription =
                userSubscriptionRepository.findByUserId(userId).orElse(null);
        if (subscription == null) {
            return userSubscriptionRepository.saveAndFlush(
                    UserSubscription.builder()
                            .userId(userId)
                            .platform(platform)
                            .productId(result.storeProductId())
                            .basePlanId(result.basePlanId())
                            .plan(plan)
                            .startedAt(result.startedAt() != null ? result.startedAt() : now)
                            .expiresAt(expiresAt)
                            .autoRenew(result.autoRenew())
                            .trialUsed(result.trial())
                            .originalTransactionId(result.originalTransactionId())
                            .purchaseToken(result.purchaseToken())
                            .build());
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
        return subscription;
    }

    /**
     * 같은 스토어 트랜잭션(원구독)을 다른 계정이 재사용하는 것을 차단한다 — 계정 간 권한 공유 방지.
     * 계정 재가입 후 Restore 도 막히므로, 정당한 이전이 필요하면 CS 로 처리한다(추후 이전 정책 검토).
     */
    private void guardCrossUserReuse(String userId, SubscriptionVerificationResult result) {
        Optional<UserSubscription> existing = Optional.empty();
        if (result.originalTransactionId() != null) {
            existing =
                    userSubscriptionRepository.findByOriginalTransactionId(
                            result.originalTransactionId());
        } else if (result.purchaseToken() != null) {
            existing = userSubscriptionRepository.findByPurchaseToken(result.purchaseToken());
        }
        if (existing.isPresent() && !existing.get().getUserId().equals(userId)) {
            log.warn(
                    "구독 트랜잭션 교차 계정 재사용 차단: 요청 userId={}, 보유 userId={}",
                    userId,
                    existing.get().getUserId());
            throw new CustomException("120802", "error.subscription.transaction_already_used");
        }
    }
}
