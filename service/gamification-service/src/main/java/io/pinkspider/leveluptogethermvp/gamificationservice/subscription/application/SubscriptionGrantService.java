package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.SubscriptionPlanMapping;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionEntitlementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerificationResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionVerifyRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * LUT-451: 구독 영수증 검증 + 권한 부여 오케스트레이터 — 최초 구매/복원 공용 경로.
 *
 * <p>외부 영수증 검증(HTTP)은 트랜잭션 밖에서 수행하고, 권한 기록만
 * {@link SubscriptionGrantTxService} 한 트랜잭션으로 묶는다 (LUT-354 패턴).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionGrantService {

    private final SubscriptionVerificationService verificationService;
    private final SubscriptionGrantTxService grantTxService;

    public SubscriptionEntitlementResponse verifyAndGrant(
            String userId, SubscriptionVerifyRequest request) {
        // 영수증 검증 (외부 HTTP — 트랜잭션 밖)
        SubscriptionVerificationResult result = verificationService.verify(request);

        // 3키 매핑 — 검증 활성 모드에선 스토어 응답의 product/base plan 기준
        SubscriptionPlan plan =
                SubscriptionPlanMapping.resolve(
                        request.getPlatform(), result.storeProductId(), result.basePlanId());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt =
                result.expiresAt() != null ? result.expiresAt() : defaultExpiresAt(plan, now);

        UserSubscription subscription;
        try {
            subscription =
                    grantTxService.upsert(userId, plan, request.getPlatform(), result, expiresAt, now);
        } catch (DataIntegrityViolationException e) {
            // 같은 유저 동시 요청 race — uk_user_subscription_user 가 이중 insert 를 막았다.
            // 행이 생겼으니 갱신(멱등) 경로로 1회 재시도.
            subscription =
                    grantTxService.upsert(userId, plan, request.getPlatform(), result, expiresAt, now);
        }

        log.info(
                "구독 권한 기록: userId={}, platform={}, plan={}, expiresAt={}, trial={}",
                userId,
                request.getPlatform(),
                plan,
                subscription.getExpiresAt(),
                subscription.getTrialUsed());
        return SubscriptionEntitlementResponse.of(subscription, now);
    }

    /** 검증 비활성(dev) 모드 기본 만료 — 플랜 기간만큼 부여 */
    private LocalDateTime defaultExpiresAt(SubscriptionPlan plan, LocalDateTime now) {
        return plan == SubscriptionPlan.ANNUAL ? now.plusYears(1) : now.plusMonths(1);
    }
}
