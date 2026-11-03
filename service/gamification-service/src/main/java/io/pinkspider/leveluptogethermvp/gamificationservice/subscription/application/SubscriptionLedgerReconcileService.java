package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleSubscriptionState;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionLedgerReconcileResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.SubscriptionPaymentHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.UserSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-617: 어드민 수동 정합 — 한 유저의 Android 구독 원장을 Play 주문 연번(1..latest)과 전부 대조한다.
 *
 * <p>RTDN 유실 구간의 누락분 백필과, 자가 치유가 늦게 기록해 시각·주문 ID 가 어긋난 행의 정정에 쓴다. 구독 행({@code user_subscription})은
 * 건드리지 않는다 — 권한 상태 동기화는 verify·RTDN·자가 치유의 몫이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionLedgerReconcileService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPaymentHistoryRepository historyRepository;
    private final SubscriptionVerificationService verificationService;
    private final GoogleOrderLedgerSync ledgerSync;

    /** LUT-677 일일 정합 대상: 만료가 이 일수 안에 있는(활성 포함) Android 구독 — 만료 직후 RTDN 유실분까지 한 번 더 본다 */
    static final int DAILY_RECENT_DAYS = 7;

    /** LUT-677: 일일 정합 대상 유저 — 활성·최근 만료(7일) Android 구독(구매 토큰 있는 것) */
    @Transactional(transactionManager = "gamificationTransactionManager", readOnly = true)
    public List<String> findAndroidDailyTargets(LocalDateTime now) {
        return userSubscriptionRepository
                .findByPlatformAndPurchaseTokenIsNotNullAndExpiresAtAfter(
                        "android", now.minusDays(DAILY_RECENT_DAYS))
                .stream()
                .map(UserSubscription::getUserId)
                .toList();
    }

    /**
     * LUT-677: 한 유저 증분 대조 — Play 재조회 후 원장의 마지막 연번 다음부터 최신까지(+ 초회 주문 STORE 정정). 스케줄러용이라 대상이 아니면 0,
     * Play 일시 실패는 {@code 120702} 로 던져 호출자가 건너뛴다. 구독 행은 테스트 결제 여부만 맞춘다.
     *
     * @return 새로 쓰거나 정정한 행 수
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public int reconcileGoogleIncremental(String userId) {
        UserSubscription subscription =
                userSubscriptionRepository.findByUserId(userId).orElse(null);
        if (subscription == null
                || !"android".equals(subscription.getPlatform())
                || subscription.getPurchaseToken() == null) {
            return 0;
        }
        GoogleSubscriptionState state;
        try {
            state = verificationService.fetchGoogleSubscription(subscription.getPurchaseToken());
        } catch (StoreTransientException e) {
            log.warn("일일 원장 정합 — Play 재조회 일시 실패: userId={}, error={}", userId, e.getMessage());
            throw new CustomException("120702", "error.iap.verification_failed");
        }
        subscription.setTestPurchase(state.testPurchase());
        return ledgerSync.syncRenewals(
                subscription,
                state.latestOrderId(),
                state.trial(),
                state.expiresAt() != null ? state.expiresAt() : subscription.getExpiresAt(),
                null,
                false,
                GoogleOrderLedgerSync.SOURCE_DAILY);
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public SubscriptionLedgerReconcileResponse reconcileGoogle(String userId) {
        UserSubscription subscription =
                userSubscriptionRepository
                        .findByUserId(userId)
                        .orElseThrow(
                                () ->
                                        new CustomException(
                                                "120803", "error.subscription.not_found"));
        if (!"android".equals(subscription.getPlatform())
                || subscription.getPurchaseToken() == null) {
            throw new CustomException("120804", "error.subscription.reconcile_unsupported");
        }

        GoogleSubscriptionState state;
        try {
            state = verificationService.fetchGoogleSubscription(subscription.getPurchaseToken());
        } catch (StoreTransientException e) {
            log.warn("구독 정합 — Play 재조회 일시 실패: userId={}, error={}", userId, e.getMessage());
            throw new CustomException("120702", "error.iap.verification_failed");
        }

        // LUT-666: 테스트 결제 여부는 권한이 아니라 구매의 성질 — 백필되는 행이 맞는 값을 갖도록 재조회 결과로 맞춘다
        subscription.setTestPurchase(state.testPurchase());
        long before = historyRepository.countByUserId(userId);
        int written =
                ledgerSync.syncRenewals(
                        subscription,
                        state.latestOrderId(),
                        state.trial(),
                        state.expiresAt() != null ? state.expiresAt() : subscription.getExpiresAt(),
                        null,
                        true);
        long after = historyRepository.countByUserId(userId);
        log.info(
                "구독 원장 정합 완료: userId={}, latestOrderId={}, state={}, rows {} → {}, written={}",
                userId,
                state.latestOrderId(),
                state.subscriptionState(),
                before,
                after,
                written);
        return new SubscriptionLedgerReconcileResponse(
                userId,
                state.latestOrderId(),
                state.subscriptionState(),
                state.expiresAt(),
                before,
                after,
                written);
    }
}
