package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.global.translation.LocaleUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "mission_category")
@Comment("미션 카테고리")
public class MissionCategory extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("카테고리 ID")
    private Long id;

    @NotNull
    @Size(max = 50)
    @Column(name = "name", nullable = false, length = 50, unique = true)
    @Comment("카테고리 이름")
    private String name;

    @Size(max = 50)
    @Column(name = "name_en", length = 50)
    @Comment("카테고리 이름 (영어)")
    private String nameEn;

    @Size(max = 50)
    @Column(name = "name_ar", length = 50)
    @Comment("카테고리 이름 (아랍어)")
    private String nameAr;

    @Size(max = 200)
    @Column(name = "description", length = 200)
    @Comment("카테고리 설명")
    private String description;

    @Size(max = 200)
    @Column(name = "description_en", length = 200)
    @Comment("카테고리 설명 (영어)")
    private String descriptionEn;

    @Size(max = 200)
    @Column(name = "description_ar", length = 200)
    @Comment("카테고리 설명 (아랍어)")
    private String descriptionAr;

    @Size(max = 50)
    @Column(name = "icon", length = 50)
    @Comment("카테고리 아이콘 (이모지 또는 아이콘 코드)")
    private String icon;

    @Column(name = "display_order")
    @Comment("표시 순서")
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    @Comment("활성화 여부")
    @lombok.Builder.Default
    private Boolean isActive = true;

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    /**
     * locale에 따라 카테고리 이름을 반환합니다.
     */
    public String getLocalizedName(String locale) {
        return LocaleUtils.getLocalizedText(name, nameEn, nameAr, locale);
    }

    /**
     * locale에 따라 카테고리 설명을 반환합니다.
     */
    public String getLocalizedDescription(String locale) {
        return LocaleUtils.getLocalizedText(description, descriptionEn, descriptionAr, locale);
    }
}
