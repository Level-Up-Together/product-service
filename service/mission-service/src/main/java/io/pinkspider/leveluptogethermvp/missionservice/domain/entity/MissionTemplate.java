package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.global.translation.LocaleUtils;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionInterval;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionParticipationType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionSource;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Table(name = "mission_template")
@Comment("미션 템플릿 (미션북)")
public class MissionTemplate extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("템플릿 ID")
    private Long id;

    @NotNull
    @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    @Comment("미션 제목")
    private String title;

    @Size(max = 200)
    @Column(name = "title_en", length = 200)
    @Comment("미션 제목 (영어)")
    private String titleEn;

    @Size(max = 200)
    @Column(name = "title_ar", length = 200)
    @Comment("미션 제목 (아랍어)")
    private String titleAr;

    @Column(name = "description", columnDefinition = "TEXT")
    @Comment("미션 설명")
    private String description;

    @Column(name = "description_en", columnDefinition = "TEXT")
    @Comment("미션 설명 (영어)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "TEXT")
    @Comment("미션 설명 (아랍어)")
    private String descriptionAr;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    @Comment("공개 여부 (PUBLIC, PRIVATE)")
    @Builder.Default
    private MissionVisibility visibility = MissionVisibility.PUBLIC;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    @Comment("출처 (SYSTEM, USER)")
    @Builder.Default
    private MissionSource source = MissionSource.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_type", length = 20)
    @Comment("참여 방식 (DIRECT, TEMPLATE_ONLY)")
    @Builder.Default
    private MissionParticipationType participationType = MissionParticipationType.DIRECT;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_interval", length = 20)
    @Comment("미션 수행 주기")
    @Builder.Default
    private MissionInterval missionInterval = MissionInterval.DAILY;

    @Column(name = "duration_minutes")
    @Comment("1회 수행 시간 (분)")
    private Integer durationMinutes;

    @Column(name = "bonus_exp_on_full_completion")
    @Comment("전체 완료시 보너스 경험치")
    @Builder.Default
    private Integer bonusExpOnFullCompletion = 50;

    @Column(name = "is_pinned", nullable = false)
    @Comment("고정 미션 여부")
    @Builder.Default
    private Boolean isPinned = false;

    @Column(name = "target_duration_minutes")
    @Comment("목표 수행 시간 (분)")
    private Integer targetDurationMinutes;

    @Column(name = "daily_execution_limit")
    @Comment("하루 수행 횟수 제한 (null = 무제한)")
    private Integer dailyExecutionLimit;

    @Column(name = "category_id")
    @Comment("카테고리 ID (스냅샷)")
    private Long categoryId;

    @Size(max = 100)
    @Column(name = "category_name", length = 100)
    @Comment("카테고리 이름 (스냅샷)")
    private String categoryName;

    @Size(max = 50)
    @Column(name = "custom_category", length = 50)
    @Comment("사용자 정의 카테고리")
    private String customCategory;

    @NotNull
    @Column(name = "creator_id", nullable = false)
    @Comment("생성자 ID")
    @Builder.Default
    private String creatorId = "ADMIN";

    /**
     * Lombok @Getter보다 우선하여 customCategory 폴백 로직 유지
     */
    public String getCategoryName() {
        if (categoryName != null) {
            return categoryName;
        }
        return customCategory;
    }

    public String getLocalizedTitle(String locale) {
        return LocaleUtils.getLocalizedText(title, titleEn, titleAr, locale);
    }

    public String getLocalizedDescription(String locale) {
        return LocaleUtils.getLocalizedText(description, descriptionEn, descriptionAr, locale);
    }
}
