package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    Optional<UserSubscription> findByUserId(String userId);

    /** LUT-451: iOS 교차 계정 재사용 가드 + LUT-452 ASSN V2 웹훅 매칭 */
    Optional<UserSubscription> findByOriginalTransactionId(String originalTransactionId);

    /** LUT-451: Android 교차 계정 재사용 가드 + LUT-452 RTDN 웹훅 매칭 */
    Optional<UserSubscription> findByPurchaseToken(String purchaseToken);
}
