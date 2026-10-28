package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.SubscriptionPaymentHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPaymentHistoryRepository
        extends JpaRepository<SubscriptionPaymentHistory, Long> {

    /** LUT-486: 멱등 기록 가드 — uk_subscription_payment_dedup 과 같은 키 */
    boolean existsByUserIdAndEventTypeAndExpiresAt(
            String userId, SubscriptionPaymentEventType eventType, LocalDateTime expiresAt);

    /** LUT-617: 같은 만료의 행 — 자가 치유가 다른 주문 ID 로 먼저 기록한 행을 찾아 주문 기준으로 정정 (uk 라 최대 1건) */
    Optional<SubscriptionPaymentHistory> findByUserIdAndEventTypeAndExpiresAt(
            String userId, SubscriptionPaymentEventType eventType, LocalDateTime expiresAt);

    /** LUT-617: 같은 주문 ID 로 이미 기록된 행 — 시각·금액 정정용 (제약은 없지만 실질 유일, 방어적으로 List) */
    List<SubscriptionPaymentHistory> findByUserIdAndEventTypeAndTransactionId(
            String userId, SubscriptionPaymentEventType eventType, String transactionId);

    /** LUT-617: 한 구매(GPA.xxxx 기준)의 주문 연번 전부 — 원장의 마지막 N 계산 */
    List<SubscriptionPaymentHistory> findByUserIdAndTransactionIdStartingWith(
            String userId, String transactionIdPrefix);

    long countByUserId(String userId);

    /**
     * LUT-488: 어드민 결제이력 통합 페이지 — 기간(occurred_at)/플랫폼/플랜/이벤트 필터 목록 조회. (:param IS NULL OR ...) 패턴의
     * PG 42P18 회피를 위한 boolean 플래그 분리는 {@code
     * DiamondBundlePurchaseRepository.searchInternal}(LUT-431) 참조. 호출은 default 메서드로.
     */
    default Page<SubscriptionPaymentHistory> search(
            LocalDateTime startAt,
            LocalDateTime endAt,
            String platform,
            SubscriptionPlan plan,
            SubscriptionPaymentEventType eventType,
            Pageable pageable) {
        return searchInternal(
                startAt != null,
                startAt,
                endAt != null,
                endAt,
                platform != null,
                platform,
                plan != null,
                plan,
                eventType != null,
                eventType,
                pageable);
    }

    @Query(
            value =
                    "SELECT sph FROM SubscriptionPaymentHistory sph "
                            + "WHERE (:startAtSet = false OR sph.occurredAt >= :startAt) "
                            + "AND (:endAtSet = false OR sph.occurredAt <= :endAt) "
                            + "AND (:platformSet = false OR sph.platform = :platform) "
                            + "AND (:planSet = false OR sph.plan = :plan) "
                            + "AND (:eventTypeSet = false OR sph.eventType = :eventType) "
                            + "ORDER BY sph.id DESC",
            countQuery =
                    "SELECT COUNT(sph) FROM SubscriptionPaymentHistory sph "
                            + "WHERE (:startAtSet = false OR sph.occurredAt >= :startAt) "
                            + "AND (:endAtSet = false OR sph.occurredAt <= :endAt) "
                            + "AND (:platformSet = false OR sph.platform = :platform) "
                            + "AND (:planSet = false OR sph.plan = :plan) "
                            + "AND (:eventTypeSet = false OR sph.eventType = :eventType)")
    Page<SubscriptionPaymentHistory> searchInternal(
            @Param("startAtSet") boolean startAtSet,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAtSet") boolean endAtSet,
            @Param("endAt") LocalDateTime endAt,
            @Param("platformSet") boolean platformSet,
            @Param("platform") String platform,
            @Param("planSet") boolean planSet,
            @Param("plan") SubscriptionPlan plan,
            @Param("eventTypeSet") boolean eventTypeSet,
            @Param("eventType") SubscriptionPaymentEventType eventType,
            Pageable pageable);

    /** LUT-488: 닉네임 매칭/유저 상세(LUT-486) — userId 목록 추가 필터 (빈 IN 무효라 별도 메서드) */
    default Page<SubscriptionPaymentHistory> searchWithUsers(
            LocalDateTime startAt,
            LocalDateTime endAt,
            String platform,
            SubscriptionPlan plan,
            SubscriptionPaymentEventType eventType,
            List<String> userIds,
            Pageable pageable) {
        return searchWithUsersInternal(
                startAt != null,
                startAt,
                endAt != null,
                endAt,
                platform != null,
                platform,
                plan != null,
                plan,
                eventType != null,
                eventType,
                userIds,
                pageable);
    }

    @Query(
            value =
                    "SELECT sph FROM SubscriptionPaymentHistory sph "
                            + "WHERE (:startAtSet = false OR sph.occurredAt >= :startAt) "
                            + "AND (:endAtSet = false OR sph.occurredAt <= :endAt) "
                            + "AND (:platformSet = false OR sph.platform = :platform) "
                            + "AND (:planSet = false OR sph.plan = :plan) "
                            + "AND (:eventTypeSet = false OR sph.eventType = :eventType) "
                            + "AND sph.userId IN :userIds "
                            + "ORDER BY sph.id DESC",
            countQuery =
                    "SELECT COUNT(sph) FROM SubscriptionPaymentHistory sph "
                            + "WHERE (:startAtSet = false OR sph.occurredAt >= :startAt) "
                            + "AND (:endAtSet = false OR sph.occurredAt <= :endAt) "
                            + "AND (:platformSet = false OR sph.platform = :platform) "
                            + "AND (:planSet = false OR sph.plan = :plan) "
                            + "AND (:eventTypeSet = false OR sph.eventType = :eventType) "
                            + "AND sph.userId IN :userIds")
    Page<SubscriptionPaymentHistory> searchWithUsersInternal(
            @Param("startAtSet") boolean startAtSet,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAtSet") boolean endAtSet,
            @Param("endAt") LocalDateTime endAt,
            @Param("platformSet") boolean platformSet,
            @Param("platform") String platform,
            @Param("planSet") boolean planSet,
            @Param("plan") SubscriptionPlan plan,
            @Param("eventTypeSet") boolean eventTypeSet,
            @Param("eventType") SubscriptionPaymentEventType eventType,
            @Param("userIds") List<String> userIds,
            Pageable pageable);
}
