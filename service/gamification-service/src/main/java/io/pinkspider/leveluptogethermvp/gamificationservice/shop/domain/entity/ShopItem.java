package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemImagePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * 상점 아이템 (QA-225)
 * 구매 시 다이아 차감: diamond_history.type=SHOP, source_id=shop_item.id (QA-220)
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "shop_item")
@Comment("상점 아이템")
public class ShopItem extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("아이템 ID")
    private Long id;

    @NotNull
    @Column(name = "name", nullable = false, length = 50)
    @Comment("아이템명")
    private String name;

    @Column(name = "name_en", length = 50)
    @Comment("아이템명 (영어)")
    private String nameEn;

    @Column(name = "name_ar", length = 50)
    @Comment("아이템명 (아랍어)")
    private String nameAr;

    @Column(name = "name_ja", length = 50)
    @Comment("아이템명 (일본어)")
    private String nameJa;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    @Comment("아이템 타입 (BASIC|FULL|HEAD|EFFECT)")
    private ShopItemType itemType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "rarity", nullable = false, length = 20)
    @Comment("희귀도")
    private TitleRarity rarity;

    @Column(name = "image_url", length = 500)
    @Comment("아이템 이미지 URL")
    private String imageUrl;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "image_position", nullable = false, length = 10)
    @Comment("이미지 포지션 (FRONT|BACK)")
    @lombok.Builder.Default
    private ShopItemImagePosition imagePosition = ShopItemImagePosition.BACK;

    @NotNull
    @Column(name = "price", nullable = false)
    @Comment("다이아 가격")
    @lombok.Builder.Default
    private Integer price = 0;

    @NotNull
    @Column(name = "is_active", nullable = false)
    @Comment("활성화 여부")
    @lombok.Builder.Default
    private Boolean isActive = true;
}
