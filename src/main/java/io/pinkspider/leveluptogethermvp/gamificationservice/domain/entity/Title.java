package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.global.translation.LocaleUtils;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
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
import lombok.Builder;
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
@Table(name = "title")
@Comment("칭호 (LEFT: 형용사형, RIGHT: 명사형 조합 시스템)")
public class Title extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("칭호 ID")
    private Long id;

    @NotNull
    @Column(name = "name", nullable = false, length = 50)
    @Comment("칭호 이름")
    private String name;

    @Column(name = "name_en", length = 50)
    @Comment("칭호 이름 (영어)")
    private String nameEn;

    @Column(name = "name_ar", length = 50)
    @Comment("칭호 이름 (아랍어)")
    private String nameAr;

    @Column(name = "description", length = 200)
    @Comment("칭호 설명")
    private String description;

    @Column(name = "description_en", length = 200)
    @Comment("칭호 설명 (영어)")
    private String descriptionEn;

    @Column(name = "description_ar", length = 200)
    @Comment("칭호 설명 (아랍어)")
    private String descriptionAr;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "rarity", nullable = false, length = 20)
    @Comment("칭호 등급")
    private TitleRarity rarity;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "position_type", nullable = false, length = 10)
    @Comment("장착 위치 타입 (LEFT: 형용사/부사형, RIGHT: 명사형)")
    private TitlePosition positionType;

    @Column(name = "color_code", length = 10)
    @Comment("색상 코드")
    private String colorCode;

    @Column(name = "icon_url")
    @Comment("칭호 아이콘 URL")
    private String iconUrl;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "acquisition_type", nullable = false, length = 20)
    @Comment("획득 방법 (LEVEL, ACHIEVEMENT, MISSION, GUILD, EVENT, SPECIAL)")
    @Builder.Default
    private TitleAcquisitionType acquisitionType = TitleAcquisitionType.ACHIEVEMENT;

    @Column(name = "acquisition_condition", length = 200)
    @Comment("획득 조건 설명")
    private String acquisitionCondition;

    @NotNull
    @Column(name = "is_active", nullable = false)
    @Comment("활성 여부")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * LEFT와 RIGHT 칭호를 조합하여 표시명을 생성합니다.
     * 예: "용감한" (LEFT) + "전사" (RIGHT) = "용감한 전사"
     * 단일 칭호인 경우 해당 칭호명만 반환합니다.
     */
    public String getDisplayName() {
        return name;
    }

    /**
     * locale에 따라 칭호명을 반환합니다.
     * @param locale Accept-Language 헤더 값
     * @return 해당 locale의 칭호명 (없으면 기본값)
     */
    public String getLocalizedName(String locale) {
        return LocaleUtils.getLocalizedText(name, nameEn, nameAr, locale);
    }

    /**
     * 두 칭호를 조합하여 표시명을 생성합니다.
     * @param leftTitle LEFT 칭호 (형용사/부사형)
     * @param rightTitle RIGHT 칭호 (명사형)
     * @return 조합된 칭호명 (예: "용감한 전사")
     */
    public static String getCombinedDisplayName(Title leftTitle, Title rightTitle) {
        return getCombinedDisplayName(leftTitle, rightTitle, null);
    }

    /**
     * 두 칭호를 조합하여 locale에 맞는 표시명을 생성합니다.
     * @param leftTitle LEFT 칭호 (형용사/부사형)
     * @param rightTitle RIGHT 칭호 (명사형)
     * @param locale Accept-Language 헤더 값
     * @return 조합된 칭호명 (예: "Brave Warrior")
     */
    public static String getCombinedDisplayName(Title leftTitle, Title rightTitle, String locale) {
        if (leftTitle == null && rightTitle == null) {
            return "";
        }
        if (leftTitle == null) {
            return rightTitle.getLocalizedName(locale);
        }
        if (rightTitle == null) {
            return leftTitle.getLocalizedName(locale);
        }
        return leftTitle.getLocalizedName(locale) + " " + rightTitle.getLocalizedName(locale);
    }

    /**
     * 이 칭호가 LEFT 타입인지 확인합니다.
     */
    public boolean isLeftPosition() {
        return TitlePosition.LEFT.equals(this.positionType);
    }

    /**
     * 이 칭호가 RIGHT 타입인지 확인합니다.
     */
    public boolean isRightPosition() {
        return TitlePosition.RIGHT.equals(this.positionType);
    }
}
