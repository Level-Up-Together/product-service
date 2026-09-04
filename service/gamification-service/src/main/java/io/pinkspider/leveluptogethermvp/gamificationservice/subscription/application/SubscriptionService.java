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
}
