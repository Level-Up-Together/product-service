package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * LUT-472: 관리자 아이템 수동 지급 이력. 칭호 부여(user_title.granted_by)와 달리 별도 테이블로 두어
 * 회수 후에도 이력이 남는다 — 회수는 revoked_at 마킹 + user_item 삭제.
 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "item_grant",
    indexes = {
        @Index(name = "idx_item_grant_user_id", columnList = "user_id"),
        @Index(name = "idx_item_grant_granted_at", columnList = "granted_at")
    })
@Comment("관리자 아이템 부여 이력")
public class ItemGrant extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("대상 유저 ID")
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_item_id", nullable = false)
    @Comment("부여 아이템")
    private ShopItem shopItem;

    @Column(name = "reason")
    @Comment("부여 사유")
    private String reason;

    @NotNull
    @Column(name = "granted_by", nullable = false)
    @Comment("부여 관리자 ID")
    private Long grantedBy;

    @NotNull
    @Column(name = "granted_at", nullable = false)
    @Comment("부여 일시")
    private LocalDateTime grantedAt;

    @Column(name = "revoked_by")
    @Comment("회수 관리자 ID")
    private Long revokedBy;

    @Column(name = "revoked_at")
    @Comment("회수 일시")
    private LocalDateTime revokedAt;

    public static ItemGrant create(String userId, ShopItem shopItem, String reason, Long adminId) {
        return ItemGrant.builder()
            .userId(userId)
            .shopItem(shopItem)
            .reason(reason)
            .grantedBy(adminId)
            .grantedAt(LocalDateTime.now())
            .build();
    }

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    public void revoke(Long adminId) {
        this.revokedBy = adminId;
        this.revokedAt = LocalDateTime.now();
    }
}
