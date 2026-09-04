package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.SubscriptionPlanMapping;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.AppleSubscriptionNotification;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleSubscriptionState;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.UserSubscriptionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-452: 스토어 웹훅 이벤트를 구독 행에 적용 — 한 트랜잭션.
 *
 * <p>모든 적용은 <b>상태 수렴형</b>(같은 이벤트를 몇 번 적용해도 같은 결과)이라 at-least-once 재전송에
 * 멱등하다. 행이 없으면(유저가 아직 /verify 전) 로그만 남기고 넘어간다 — 이후 /verify·Restore 가
 * 최신 상태로 등록한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionWebhookTxService {

    private final UserSubscriptionRepository userSubscriptionRepository;

    // ========== Apple (ASSN V2) ==========

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void applyAppleNotification(AppleSubscriptionNotification notification) {
        JWSTransactionDecodedPayload transaction = notification.transaction();
        if (transaction == null || transaction.getOriginalTransactionId() == null) {
            log.info("ASSN 트랜잭션 없는 알림 스킵: type={}", notification.notificationType());
            return;
        }
        UserSubscription subscription =
                userSubscriptionRepository
                        .findByOriginalTransactionId(transaction.getOriginalTransactionId())
                        .orElse(null);
        if (subscription == null) {
            // 유저가 아직 /verify 를 호출하지 않은 구매 — 이후 verify/Restore 가 등록한다
            log.warn(
                    "ASSN 매칭 구독 행 없음 — 스킵: type={}, originalTransactionId={}",
                    notification.notificationType(),
                    transaction.getOriginalTransactionId());
            return;
        }

        String type = notification.notificationType();
        JWSRenewalInfoDecodedPayload renewalInfo = notification.renewalInfo();
        switch (type) {
            // 구매·갱신·플랜 변경 반영·오퍼 적용 — 트랜잭션 기준으로 동기화
            case "SUBSCRIBED", "DID_RENEW", "OFFER_REDEEMED", "DID_CHANGE_RENEWAL_PREF" ->
                    syncFromTransaction(subscription, transaction, renewalInfo);
            // 자동갱신 켬/끔 (해지 = AUTO_RENEW_DISABLED — 만료까지 권한 유지)
            case "DID_CHANGE_RENEWAL_STATUS" -> {
                applyAutoRenewStatus(subscription, renewalInfo);
                log.info(
                        "ASSN 자동갱신 변경: userId={}, autoRenew={}",
                        subscription.getUserId(),
                        subscription.getAutoRenew());
            }
            // 갱신 결제 실패 — GRACE_PERIOD subtype 이면 유예기간 진입(권한 유지)
            case "DID_FAIL_TO_RENEW" -> {
                if (renewalInfo != null && renewalInfo.getGracePeriodExpiresDate() != null) {
                    subscription.enterGracePeriod(
                            SubscriptionVerificationService.toLocalDateTime(
                                    renewalInfo.getGracePeriodExpiresDate()));
                    log.info(
                            "ASSN 유예기간 진입: userId={}, graceUntil={}",
                            subscription.getUserId(),
                            subscription.getGracePeriodExpiresAt());
                }
            }
            // 유예기간 이탈(미복구 종료)
            case "GRACE_PERIOD_EXPIRED" -> {
                subscription.setGracePeriodExpiresAt(null);
                subscription.setAutoRenew(false);
                log.info("ASSN 유예기간 만료: userId={}", subscription.getUserId());
            }
            // 만료 (자발적 해지 후 기간 종료 등)
            case "EXPIRED" -> {
                subscription.setGracePeriodExpiresAt(null);
                subscription.setAutoRenew(false);
                if (transaction.getExpiresDate() != null) {
                    subscription.setExpiresAt(
                            SubscriptionVerificationService.toLocalDateTime(
                                    transaction.getExpiresDate()));
                }
                log.info("ASSN 구독 만료: userId={}", subscription.getUserId());
            }
            // 환불·회수 — 권한 즉시 종료
            case "REFUND", "REVOKE" -> {
                LocalDateTime revokedAt =
                        transaction.getRevocationDate() != null
                                ? SubscriptionVerificationService.toLocalDateTime(
                                        transaction.getRevocationDate())
                                : LocalDateTime.now();
                revoke(subscription, revokedAt);
                log.info("ASSN 환불/회수 — 권한 종료: userId={}, type={}", subscription.getUserId(), type);
            }
            // 가격 변경 동의 (subtype ACCEPTED) / 예정(PENDING) — 상태 변화 없음, 기록만
            case "PRICE_INCREASE" ->
                    log.info(
                            "ASSN 가격 변경 알림: userId={}, subtype={}",
                            subscription.getUserId(),
                            notification.subtype());
            default -> log.info("ASSN 미처리 타입 — 스킵: type={}, subtype={}", type, notification.subtype());
        }
    }

    /** 트랜잭션 payload 기준 동기화 — 갱신은 만료 연장 + 유예 해제, 플랜 변경은 상품/플랜 교체 */
    private void syncFromTransaction(
            UserSubscription subscription,
            JWSTransactionDecodedPayload transaction,
            JWSRenewalInfoDecodedPayload renewalInfo) {
        if (transaction.getProductId() != null) {
            subscription.setProductId(transaction.getProductId());
            subscription.setPlan(
                    SubscriptionPlanMapping.resolve("ios", transaction.getProductId(), null));
        }
        if (transaction.getExpiresDate() != null) {
            subscription.renew(
                    SubscriptionVerificationService.toLocalDateTime(transaction.getExpiresDate()));
        }
        applyAutoRenewStatus(subscription, renewalInfo);
        if (transaction.getRawOfferType() != null && transaction.getRawOfferType() == 1) {
            subscription.setTrialUsed(true);
        }
        log.info(
                "ASSN 구독 동기화: userId={}, plan={}, expiresAt={}",
                subscription.getUserId(),
                subscription.getPlan(),
                subscription.getExpiresAt());
    }

    /** renewalInfo 의 autoRenewStatus(1=켬, 0=끔) 반영 — 없으면 유지 */
    private void applyAutoRenewStatus(
            UserSubscription subscription, JWSRenewalInfoDecodedPayload renewalInfo) {
        if (renewalInfo != null && renewalInfo.getRawAutoRenewStatus() != null) {
            subscription.setAutoRenew(renewalInfo.getRawAutoRenewStatus() == 1);
        }
    }

    // ========== Google (RTDN) ==========

    /** RTDN 트리거 후 subscriptionsv2 재조회 상태를 적용한다 — 웹훅이 진실의 원천이라 만료 단축도 반영 */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void applyGoogleState(String purchaseToken, GoogleSubscriptionState state) {
        UserSubscription subscription =
                userSubscriptionRepository.findByPurchaseToken(purchaseToken).orElse(null);
        if (subscription == null) {
            log.warn("RTDN 매칭 구독 행 없음 — 스킵: state={}", state.subscriptionState());
            return;
        }
        if (state.isPending()) {
            log.info("RTDN 결제 대기 상태 — 스킵: userId={}", subscription.getUserId());
            return;
        }

        subscription.setProductId(state.productId());
        subscription.setBasePlanId(state.basePlanId());
        subscription.setPlan(
                SubscriptionPlanMapping.resolve("android", state.productId(), state.basePlanId()));
        subscription.setExpiresAt(state.expiresAt());
        subscription.setAutoRenew(state.autoRenew());
        if (state.isInGracePeriod()) {
            // Google 은 유예 종료 시각을 직접 주지 않는다 — expiryTime 이 유예를 반영한 값이라 그대로 쓴다
            subscription.enterGracePeriod(state.expiresAt());
        } else {
            subscription.setGracePeriodExpiresAt(null);
        }
        if (state.trial()) {
            subscription.setTrialUsed(true);
        }
        log.info(
                "RTDN 구독 동기화: userId={}, state={}, plan={}, expiresAt={}, autoRenew={}",
                subscription.getUserId(),
                state.subscriptionState(),
                subscription.getPlan(),
                subscription.getExpiresAt(),
                subscription.getAutoRenew());
    }

    /** 환불/회수(REVOKED·voidedPurchase) — 권한 즉시 종료 */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void revokeByPurchaseToken(String purchaseToken) {
        UserSubscription subscription =
                userSubscriptionRepository.findByPurchaseToken(purchaseToken).orElse(null);
        if (subscription == null) {
            log.warn("RTDN 환불 매칭 구독 행 없음 — 스킵");
            return;
        }
        revoke(subscription, LocalDateTime.now());
        log.info("RTDN 환불/회수 — 권한 종료: userId={}", subscription.getUserId());
    }

    /** 권한 즉시 종료 — 만료를 회수 시각으로 당기고 유예·자동갱신 해제 */
    private void revoke(UserSubscription subscription, LocalDateTime revokedAt) {
        if (revokedAt.isBefore(subscription.getExpiresAt())) {
            subscription.setExpiresAt(revokedAt);
        }
        subscription.setGracePeriodExpiresAt(null);
        subscription.setAutoRenew(false);
    }
}
