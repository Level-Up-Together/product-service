package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.global.translation.LocaleUtils;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.AchievementCategory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.AchievementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "achievement",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_achievement_type",
        columnNames = {"achievement_type"}
    )
)
@Comment("업적")
public class Achievement extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("업적 ID")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_type", nullable = false, length = 50)
    @Comment("업적 타입")
    private AchievementType achievementType;

    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    @Comment("업적 이름")
    private String name;

    @Column(name = "name_en", length = 100)
    @Comment("업적 이름 (영어)")
    private String nameEn;

    @Column(name = "name_ar", length = 100)
    @Comment("업적 이름 (아랍어)")
    private String nameAr;

    @Column(name = "description", length = 500)
    @Comment("업적 설명")
    private String description;

    @Column(name = "description_en", length = 500)
    @Comment("업적 설명 (영어)")
    private String descriptionEn;

    @Column(name = "description_ar", length = 500)
    @Comment("업적 설명 (아랍어)")
    private String descriptionAr;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    @Comment("업적 카테고리")
    private AchievementCategory category;

    @Column(name = "icon_url")
    @Comment("업적 아이콘 URL")
    private String iconUrl;

    @NotNull
    @Column(name = "required_count", nullable = false)
    @Comment("달성 필요 횟수")
    private Integer requiredCount;

    @NotNull
    @Column(name = "reward_exp", nullable = false)
    @Comment("보상 경험치")
    @Builder.Default
    private Integer rewardExp = 0;

    @Column(name = "reward_title_id")
    @Comment("보상 칭호 ID")
    private Long rewardTitleId;

    @Column(name = "reward_points")
    @Comment("보상 포인트")
    @Builder.Default
    private Integer rewardPoints = 0;

    @NotNull
    @Column(name = "is_hidden", nullable = false)
    @Comment("숨김 업적 여부")
    @Builder.Default
    private Boolean isHidden = false;

    @NotNull
    @Column(name = "is_active", nullable = false)
    @Comment("활성 여부")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * locale에 따라 업적명을 반환합니다.
     */
    public String getLocalizedName(String locale) {
        return LocaleUtils.getLocalizedText(name, nameEn, nameAr, locale);
    }

    /**
     * locale에 따라 업적 설명을 반환합니다.
     */
    public String getLocalizedDescription(String locale) {
        return LocaleUtils.getLocalizedText(description, descriptionEn, descriptionAr, locale);
    }
}
