package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 핑크다이아 묶음상품 (LUT-356)
 *
 * <p>상점에서 결제로 구매하는 핑크다이아 N개 묶음. 이름/설명 다국어는 아이템(ShopItem) 패턴을
 * 따라 원본 그대로 내려 프론트에서 locale 처리한다. 결제 연동·가격은 후속 티켓 범위.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "diamond_bundle")
@Comment("핑크다이아 묶음상품")
public class DiamondBundle extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("묶음상품 ID")
    private Long id;

    @NotNull
    @Column(name = "name", nullable = false, length = 50)
    @Comment("상품명")
    private String name;

    @Column(name = "name_en", length = 50)
    @Comment("상품명 (영어)")
    private String nameEn;

    @Column(name = "name_ar", length = 50)
    @Comment("상품명 (아랍어)")
    private String nameAr;

    @Column(name = "name_ja", length = 50)
    @Comment("상품명 (일본어)")
    private String nameJa;

    @Column(name = "description", length = 2000)
    @Comment("상품 설명")
    private String description;

    @Column(name = "description_en", length = 2000)
    @Comment("상품 설명 (영어)")
    private String descriptionEn;

    @Column(name = "description_ar", length = 2000)
    @Comment("상품 설명 (아랍어)")
    private String descriptionAr;

    @Column(name = "description_ja", length = 2000)
    @Comment("상품 설명 (일본어)")
    private String descriptionJa;

    @NotNull
    @Column(name = "diamond_count", nullable = false)
    @Comment("핑크다이아 개수")
    private Integer diamondCount;

    /**
     * LUT-354: 스토어 IAP 상품 ID (App Store Connect / Play Console 등록 ID).
     * 가격은 스토어 콘솔에 등록하고 앱이 localizedPrice 를 받아 표시한다.
     */
    @Column(name = "store_product_id", length = 100)
    @Comment("스토어 IAP 상품 ID")
    private String storeProductId;

    @Column(name = "image_url", length = 500)
    @Comment("상품 이미지 URL")
    private String imageUrl;

    @NotNull
    @Column(name = "is_active", nullable = false)
    @Comment("활성화 여부")
    @lombok.Builder.Default
    private Boolean isActive = true;
}
