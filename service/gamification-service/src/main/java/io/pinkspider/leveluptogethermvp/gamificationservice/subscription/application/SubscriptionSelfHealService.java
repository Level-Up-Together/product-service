package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.AppleSubscriptionSnapshot;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.GoogleSubscriptionState;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LUT-499: 구독 상태 자가 치유 — 스토어 서버 알림(RTDN/ASSN)이 지연·유실됐을 때의 안전망.
 *
 * <p>{@code expires_at} 이 지났는데 {@code auto_renew} 가 켜져 있으면 "스토어는 갱신했는데 우리만 모르는" 상태일
 * 가능성이 높다. 이때 응답 전에 스토어를 재조회해 웹훅과 같은 경로({@link SubscriptionWebhookTxService})로 덮어쓴다.
 * 알림 payload 를 신뢰하지 않는 원칙과 같다 — 진실은 항상 스토어 재조회 결과다.
 *
 * <p>재조회 실패는 삼킨다: 자가 치유는 best-effort 이고, 실패해도 DB 값으로 응답해야 화면이 뜬다. 스토어가 실제로
 * 만료·해지를 알려주면 {@code auto_renew=false} 로 수렴해 다음 호출부터는 재조회하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionSelfHealService {

    private final SubscriptionVerificationService verificationService;
    private final SubscriptionWebhookTxService webhookTxService;

    /** 재조회가 필요한 상태인지 — 만료(유예 포함) 지났는데 자동갱신이 켜져 있는 경우 */
    public boolean isStale(UserSubscription subscription, LocalDateTime now) {
        return subscription != null
                && Boolean.TRUE.equals(subscription.getAutoRenew())
                && !subscription.isEntitled(now);
    }

    /**
     * 만료됐는데 자동갱신 중인 구독을 스토어 기준으로 동기화한다.
     *
     * @return 스토어 재조회·반영이 수행됐으면 true (호출자는 행을 다시 읽어야 한다). 대상이 아니거나 실패하면 false
     */
    public boolean syncIfStale(UserSubscription subscription, LocalDateTime now) {
        if (!isStale(subscription, now)) {
            return false;
        }
        try {
            if ("android".equals(subscription.getPlatform())) {
                if (subscription.getPurchaseToken() == null) {
                    return false;
                }
                GoogleSubscriptionState state =
                        verificationService.fetchGoogleSubscription(subscription.getPurchaseToken());
                webhookTxService.applyGoogleState(subscription.getPurchaseToken(), state);
            } else {
                if (subscription.getOriginalTransactionId() == null) {
                    return false;
                }
                AppleSubscriptionSnapshot snapshot =
                        verificationService.fetchAppleLatestSubscription(
                                subscription.getOriginalTransactionId());
                webhookTxService.applyAppleSnapshot(subscription.getOriginalTransactionId(), snapshot);
            }
            log.info(
                    "구독 자가 치유 수행: userId={}, platform={}, 이전만료={}",
                    subscription.getUserId(),
                    subscription.getPlatform(),
                    subscription.getExpiresAt());
            return true;
        } catch (Exception e) {
            // best-effort — 스토어 장애·자격증명 문제로 화면이 막히면 안 된다
            log.warn(
                    "구독 자가 치유 실패 — DB 값으로 응답: userId={}, platform={}, error={}",
                    subscription.getUserId(),
                    subscription.getPlatform(),
                    e.getMessage());
            return false;
        }
    }
}
