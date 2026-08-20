package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.enums.DiamondPurchaseStatus;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * 핑크다이아 묶음상품 IAP 구매 기록 (LUT-354)
 *
 * <p>스토어 트랜잭션 ID 유니크 제약이 멱등 지급의 핵심 — 같은 영수증으로 재요청(네트워크 재시도,
 * 앱 재시작 후 pending 재처리)해도 지급은 1회만 일어난다.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "diamond_bundle_purchase",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_bundle_purchase_transaction",
        columnNames = {"store_transaction_id"}),
    indexes = @Index(name = "idx_bundle_purchase_user", columnList = "user_id, id"))
@Comment("핑크다이아 묶음상품 IAP 구매 기록")
public class DiamondBundlePurchase extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("구매 기록 ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private String userId;

    @NotNull
    @Column(name = "bundle_id", nullable = false)
    @Comment("묶음상품 ID")
    private Long bundleId;

    @NotNull
    @Column(name = "platform", nullable = false, length = 10)
    @Comment("결제 플랫폼 (ios|android)")
    private String platform;

    @NotNull
    @Column(name = "store_product_id", nullable = false, length = 100)
    @Comment("스토어 상품 ID")
    private String storeProductId;

    @NotNull
    @Column(name = "store_transaction_id", nullable = false, length = 500)
    @Comment("스토어 트랜잭션 ID (멱등 키)")
    private String storeTransactionId;

    @NotNull
    @Column(name = "diamond_count", nullable = false)
    @Comment("지급된 핑크다이아 개수")
    private Integer diamondCount;

    @Column(name = "price_amount", precision = 12, scale = 2)
    @Comment("실제 결제 금액 (LUT-401, 검증 응답에서 확보 — 확보 실패 시 null)")
    private BigDecimal priceAmount;

    @Column(name = "price_currency", length = 3)
    @Comment("결제 통화 (ISO 4217, LUT-401)")
    private String priceCurrency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("결제 상태 (LUT-401, 구매 시점엔 항상 PAID)")
    @Builder.Default
    private DiamondPurchaseStatus status = DiamondPurchaseStatus.PAID;

    @Column(name = "refunded_at")
    @Comment("환불 처리 일시 (LUT-401, 자동 감지는 후속 범위)")
    private LocalDateTime refundedAt;
}
