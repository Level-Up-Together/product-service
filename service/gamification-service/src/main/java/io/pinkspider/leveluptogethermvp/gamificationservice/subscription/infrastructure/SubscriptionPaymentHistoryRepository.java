package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPaymentHistoryRepository
        extends JpaRepository<SubscriptionPaymentHistory, Long> {

    /** LUT-486: 어드민 유저 상세 결제 이력 탭 — 최신순 */
    Page<SubscriptionPaymentHistory> findByUserIdOrderByIdDesc(String userId, Pageable pageable);

    /** LUT-486: 멱등 기록 가드 — uk_subscription_payment_dedup 과 같은 키 */
    boolean existsByUserIdAndEventTypeAndExpiresAt(
            String userId, SubscriptionPaymentEventType eventType, LocalDateTime expiresAt);
}
