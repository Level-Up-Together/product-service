package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto.SubscriptionEntitlementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure.UserSubscriptionRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final SubscriptionSelfHealService selfHealService;

    /**
     * 내 구독 권한. LUT-499: 만료됐는데 자동갱신 중이면(= 스토어 알림 유실 가능) 응답 전에 스토어를 재조회해 동기화한다.
     *
     * <p>클래스 기본이 readOnly 트랜잭션이라 그 안에서 동기화 쓰기를 호출하면 flush 되지 않는다 — 이 메서드만
     * 트랜잭션 밖(NOT_SUPPORTED)에서 조회하고, 자가 치유 쓰기는 {@link SubscriptionWebhookTxService} 가 자기
     * 트랜잭션으로 수행한 뒤 행을 다시 읽는다.
     */
    @Transactional(
            transactionManager = "gamificationTransactionManager",
            propagation = Propagation.NOT_SUPPORTED)
    public SubscriptionEntitlementResponse getMyEntitlement(String userId) {
        LocalDateTime now = LocalDateTime.now();
        UserSubscription subscription = userSubscriptionRepository.findByUserId(userId).orElse(null);
        if (subscription == null) {
            return SubscriptionEntitlementResponse.none();
        }
        if (selfHealService.syncIfStale(subscription, now)) {
            subscription = userSubscriptionRepository.findByUserId(userId).orElse(subscription);
        }
        return SubscriptionEntitlementResponse.of(subscription, now);
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
