package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity.UserSubscription;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    Optional<UserSubscription> findByUserId(String userId);

    /** LUT-453: 권한 보유(활성 또는 유예기간) 구독 전체 — 일일 스티펜드 지급 대상 */
    @Query(
            "select s from UserSubscription s where s.expiresAt > :now"
                    + " or (s.gracePeriodExpiresAt is not null and s.gracePeriodExpiresAt > :now)")
    List<UserSubscription> findAllEntitled(@Param("now") LocalDateTime now);

    /** LUT-455: 입력 유저 중 권한 보유(활성/유예기간) 유저 ID — 피드 작성자 뱃지 배치 조회 */
    @Query(
            "select s.userId from UserSubscription s where s.userId in :userIds"
                    + " and (s.expiresAt > :now"
                    + " or (s.gracePeriodExpiresAt is not null and s.gracePeriodExpiresAt > :now))")
    List<String> findEntitledUserIds(
            @Param("userIds") List<String> userIds, @Param("now") LocalDateTime now);

    /** LUT-451: iOS 교차 계정 재사용 가드 + LUT-452 ASSN V2 웹훅 매칭 */
    Optional<UserSubscription> findByOriginalTransactionId(String originalTransactionId);

    /** LUT-451: Android 교차 계정 재사용 가드 + LUT-452 RTDN 웹훅 매칭 */
    Optional<UserSubscription> findByPurchaseToken(String purchaseToken);
}
