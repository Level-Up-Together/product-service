package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPaymentEventType;
import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * 구독 결제 이력 (LUT-486) — 어드민 CS/환불 대응용 append-only 원장.
 *
 * <p>{@link UserSubscription}은 유저당 1행 갱신 방식이라 결제 건별 기록이 남지 않는다. 이 테이블은
 * verify/웹훅이 구독 만료를 <b>엄격히 연장할 때만</b> 한 행씩 적재해, verify·웹훅 이중 도착과
 * at-least-once 재전송에 자연 멱등이다. uk (user_id, event_type, expires_at)는 동시성 레이스의
 * 최후 방어선.
 *
 * <p>가격은 iOS JWS payload만 제공한다(best-effort, LUT-401 패턴) — Android subscriptionsv2
 * 응답에는 실결제가가 없어 null.
 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "subscription_payment_history",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_subscription_payment_dedup",
                        columnNames = {"user_id", "event_type", "expires_at"}),
        indexes = @Index(name = "idx_subscription_payment_user", columnList = "user_id"))
@Comment("구독 결제 이력 (LUT-486)")
public class SubscriptionPaymentHistory extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("이력 ID")
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
    @Comment("스토어 상품 ID")
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
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    @Comment("이벤트 타입 (PURCHASE|RENEWAL|REFUND)")
    private SubscriptionPaymentEventType eventType;

    @NotNull
    @Column(name = "trial", nullable = false)
    @Comment("무료 체험/introductory offer 결제 여부")
    private Boolean trial;

    @Column(name = "price_amount", precision = 12, scale = 2)
    @Comment("결제 금액 (iOS만 캡처 가능 — Android는 null)")
    private BigDecimal priceAmount;

    @Column(name = "price_currency", length = 3)
    @Comment("결제 통화 (ISO 4217)")
    private String priceCurrency;

    @Column(name = "transaction_id", length = 500)
    @Comment("결제 건 트랜잭션 ID (iOS transactionId — Android는 null)")
    private String transactionId;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    @Comment("이 결제로 확보된 기간 종료 시각 (REFUND는 권한 회수 시각)")
    private LocalDateTime expiresAt;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    @Comment("이벤트 발생 시각")
    private LocalDateTime occurredAt;
}
