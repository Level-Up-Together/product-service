package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * 유저 구독 (LUT-450)
 *
 * <p>유저당 1행 — 갱신/플랜 변경/해지는 이 행을 갱신한다(이력은 스토어가 원장). 상태는 컬럼으로
 * 저장하지 않고 {@link #resolveStatus(LocalDateTime)}로 시각 기준 파생한다 — 만료·유예 전환은
 * 시간 경과만으로 일어나므로 상태 컬럼은 필연적으로 낡는다.
 *
 * <p>프론트의 구독 상태 단일 출처는 {@code GET /api/v1/subscriptions/me} — 결제 응답으로 로컬 상태를
 * 갱신하는 패턴(다이아 잔액 방식)을 쓰면 자동갱신·해지 시점에 서버와 어긋난다.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "user_subscription",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_user_subscription_user", columnNames = {"user_id"}),
            // LUT-451: 같은 스토어 원구독을 두 계정이 나눠 갖는 것을 DB 레벨에서도 차단
            @UniqueConstraint(
                    name = "uk_user_subscription_original_tx",
                    columnNames = {"original_transaction_id"}),
            @UniqueConstraint(
                    name = "uk_user_subscription_purchase_token",
                    columnNames = {"purchase_token"})
        })
@Comment("유저 구독 (LUT-450)")
public class UserSubscription extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("구독 ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private String userId;

    @NotNull
    @Column(name = "platform", nullable = false, length = 10)
    @Comment("결제 플랫폼 (ios|android)")
    private String platform;

    @NotNull
    @Column(name = "product_id", nullable = false, length = 100)
    @Comment("스토어 상품 ID (ios: membership_1m|membership_1y, android: membership)")
    private String productId;

    @Column(name = "base_plan_id", length = 50)
    @Comment("Android base plan ID (1m|1y) — iOS는 null")
    private String basePlanId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    @Comment("내부 플랜 (MONTHLY|ANNUAL)")
    private SubscriptionPlan plan;

    @NotNull
    @Column(name = "started_at", nullable = false)
    @Comment("최초 구독 시작 시각")
    private LocalDateTime startedAt;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    @Comment("만료 시각 (갱신 시 연장)")
    private LocalDateTime expiresAt;

    @NotNull
    @Column(name = "auto_renew", nullable = false)
    @Comment("자동갱신 여부")
    private Boolean autoRenew;

    @Column(name = "grace_period_expires_at")
    @Comment("유예기간 종료 시각 (결제 재시도 중 — null이면 유예 없음)")
    private LocalDateTime gracePeriodExpiresAt;

    @NotNull
    @Column(name = "trial_used", nullable = false)
    @Comment("무료 체험 사용 여부")
    private Boolean trialUsed;

    @Column(name = "original_transaction_id", length = 500)
    @Comment("iOS originalTransactionId — 갱신 웹훅(ASSN V2) 매칭 키")
    private String originalTransactionId;

    @Column(name = "purchase_token", length = 1000)
    @Comment("Android purchaseToken — 갱신 웹훅(RTDN) 매칭 키")
    private String purchaseToken;

    /** 시각 기준 상태 파생 — 만료 전 ACTIVE, 만료 후 유예기간 내 GRACE_PERIOD, 그 외 EXPIRED. */
    public SubscriptionStatus resolveStatus(LocalDateTime now) {
        if (expiresAt.isAfter(now)) {
            return SubscriptionStatus.ACTIVE;
        }
        if (gracePeriodExpiresAt != null && gracePeriodExpiresAt.isAfter(now)) {
            return SubscriptionStatus.GRACE_PERIOD;
        }
        return SubscriptionStatus.EXPIRED;
    }

    /** 구독 권한(entitlement) 보유 여부 — 유예기간에도 권한은 유지된다. */
    public boolean isEntitled(LocalDateTime now) {
        return resolveStatus(now).isEntitled();
    }

    /** 유예기간 진입 (갱신 결제 실패 — LUT-452 웹훅에서 사용) */
    public void enterGracePeriod(LocalDateTime gracePeriodExpiresAt) {
        this.gracePeriodExpiresAt = gracePeriodExpiresAt;
    }

    /** 갱신 성공 — 만료 연장 + 유예 해제 (LUT-451/452에서 사용) */
    public void renew(LocalDateTime newExpiresAt) {
        this.expiresAt = newExpiresAt;
        this.gracePeriodExpiresAt = null;
    }
}
