package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity;

import io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.enums.SubscriptionPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-600: 플랜 × 플랫폼 × 통화별 정가. 스토어가 결제 금액을 주지 않는 Android 구독 원장을 채우는 폴백.
 *
 * <p>가격이 바뀌면 행을 지우지 말고 새 {@code effective_from} 행을 추가한다 — 결제일 이전 가장 최근 정가가 적용된다. 정산 정본은 스토어 재무
 * 리포트이고 이 값은 "집계용 추정"이다 (금액 출처는 {@code price_source=LIST} 로 남는다).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "plan_list_price",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_plan_list_price",
                        columnNames = {"plan", "platform", "currency", "effective_from"}))
@Comment("구독 플랜 정가 (LUT-600)")
public class PlanListPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    @Comment("내부 플랜 (MONTHLY/ANNUAL)")
    private SubscriptionPlan plan;

    @Column(name = "platform", nullable = false, length = 10)
    @Comment("스토어 플랫폼 (ios/android — user_subscription.platform 과 동일 표기)")
    private String platform;

    @Column(name = "currency", nullable = false, length = 3)
    @Comment("통화 (ISO 4217)")
    private String currency;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    @Comment("정가")
    private BigDecimal amount;

    @Column(name = "effective_from", nullable = false)
    @Comment("이 정가가 적용되기 시작한 날")
    private LocalDate effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("생성 일시")
    private LocalDateTime createdAt;

    public static PlanListPrice of(
            SubscriptionPlan plan,
            String platform,
            String currency,
            BigDecimal amount,
            LocalDate effectiveFrom) {
        PlanListPrice price = new PlanListPrice();
        price.plan = plan;
        price.platform = platform;
        price.currency = currency;
        price.amount = amount;
        price.effectiveFrom = effectiveFrom;
        price.createdAt = LocalDateTime.now();
        return price;
    }
}
