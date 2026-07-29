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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
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
 * LUT-296: 유저 보유 아이템 (아이템 인벤토리). user_title 패턴을 따르며, 장착은 아이템 타입당 최대 1개
 * (전환은 {@code UserItemService.equipItem}에서 같은 타입 기존 장착 해제로 보장).
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "user_item",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_item",
        columnNames = {"user_id", "shop_item_id"}
    ),
    indexes = @Index(name = "idx_user_item_user_id", columnList = "user_id"))
@Comment("유저 보유 아이템")
public class UserItem extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("유저 ID")
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_item_id", nullable = false)
    @Comment("상점 아이템")
    private ShopItem shopItem;

    @NotNull
    @Column(name = "is_equipped", nullable = false)
    @Comment("장착 여부")
    @Builder.Default
    private Boolean isEquipped = false;

    @Column(name = "acquired_at")
    @Comment("획득 일시")
    private LocalDateTime acquiredAt;

    public void equip() {
        this.isEquipped = true;
    }

    public void unequip() {
        this.isEquipped = false;
    }
}
