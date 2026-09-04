package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionEntitlementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.UserSubscriptionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구독 권한(entitlement) 조회 (LUT-450)
 *
 * <p>프론트는 이 API를 읽기만 한다 — 결제 응답으로 로컬 상태를 갱신하지 않는다. 구독 행 생성/갱신은
 * 영수증 검증(LUT-451)·갱신 웹훅(LUT-452)의 몫.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class SubscriptionService {

    private final UserSubscriptionRepository userSubscriptionRepository;

    public SubscriptionEntitlementResponse getMyEntitlement(String userId) {
        return userSubscriptionRepository
                .findByUserId(userId)
                .map(subscription ->
                        SubscriptionEntitlementResponse.of(subscription, LocalDateTime.now()))
                .orElseGet(SubscriptionEntitlementResponse::none);
    }

    /** LUT-454: 구독 권한 보유 여부 — 활성/유예기간이면 true. 파사드(통계 게이팅)에서 사용. */
    public boolean isEntitled(String userId) {
        return userSubscriptionRepository
                .findByUserId(userId)
                .map(subscription -> subscription.isEntitled(LocalDateTime.now()))
                .orElse(false);
    }

    /** LUT-455: 권한 보유 유저 ID 배치 조회 — 피드 작성자 is_subscriber 뱃지용 (IN 1쿼리) */
    public java.util.Set<String> getEntitledUserIds(java.util.List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(
                userSubscriptionRepository.findEntitledUserIds(userIds, LocalDateTime.now()));
    }
}
