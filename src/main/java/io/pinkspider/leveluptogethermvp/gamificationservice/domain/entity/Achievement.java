package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.global.translation.LocaleUtils;
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
    indexes = {
        @Index(name = "idx_achievement_category", columnList = "category_id"),
        @Index(name = "idx_achievement_mission_category", columnList = "mission_category_id"),
        @Index(name = "idx_achievement_check_logic_type", columnList = "check_logic_type_id")
    }
)
@Comment("업적")
public class Achievement extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("업적 ID")
    private Long id;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @Comment("업적 카테고리")
    private AchievementCategory category;

    @Column(name = "category_code", length = 30)
    @Comment("업적 카테고리 코드 (비정규화)")
    private String categoryCode;

    @Column(name = "mission_category_id")
    @Comment("미션 카테고리 ID (카테고리가 MISSION인 경우)")
    private Long missionCategoryId;

    @Column(name = "mission_category_name", length = 50)
    @Comment("미션 카테고리명 (비정규화)")
    private String missionCategoryName;

    @Column(name = "check_logic_type_id")
    @Comment("체크 로직 유형 ID")
    private Long checkLogicTypeId;

    @Column(name = "check_logic_data_source", length = 50)
    @Comment("데이터 소스 (비정규화)")
    private String checkLogicDataSource;

    @Column(name = "check_logic_data_field", length = 100)
    @Comment("데이터 필드 (비정규화)")
    private String checkLogicDataField;

    @Column(name = "comparison_operator", length = 20)
    @Comment("비교 연산자 (비정규화)")
    @Builder.Default
    private String comparisonOperator = "GTE";

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

    /**
     * 카테고리 설정 시 코드도 함께 저장
     */
    public void setCategory(AchievementCategory category) {
        this.category = category;
        if (category != null) {
            this.categoryCode = category.getCode();
        }
    }
}
