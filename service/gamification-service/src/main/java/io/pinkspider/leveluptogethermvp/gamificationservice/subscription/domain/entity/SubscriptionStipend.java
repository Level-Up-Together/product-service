package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * 구독 일일 스티펜드 지급 기록 (LUT-453)
 *
 * <p>멱등키 = (구독 ID, 지급일 UTC) 유니크 제약 — 스케줄러 재실행·동시 실행에도 중복 지급이 불가능하다.
 * 다이아 원장(diamond_history, type=SUBSCRIPTION)과 한 트랜잭션으로 기록된다.
 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "subscription_stipend",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_subscription_stipend_daily",
                        columnNames = {"subscription_id", "stipend_date"}),
        indexes = @Index(name = "idx_subscription_stipend_user", columnList = "user_id, id"))
@Comment("구독 일일 스티펜드 지급 기록 (LUT-453)")
public class SubscriptionStipend extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("스티펜드 지급 ID")
    private Long id;

    @NotNull
    @Column(name = "subscription_id", nullable = false)
    @Comment("구독 ID (user_subscription.id)")
    private Long subscriptionId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private String userId;

    @NotNull
    @Column(name = "stipend_date", nullable = false)
    @Comment("지급일 (UTC 기준)")
    private LocalDate stipendDate;

    @NotNull
    @Column(name = "amount", nullable = false)
    @Comment("지급 다이아 수 (블루)")
    private Integer amount;
}
