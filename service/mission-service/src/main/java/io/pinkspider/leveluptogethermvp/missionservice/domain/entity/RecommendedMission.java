package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.global.translation.LocaleUtils;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-782: 첫 미션 온보딩 추천 미션 — 누르면 바로 시작되는 카드. 어드민(LUT-783)이 이름 4개 언어·이모지·카테고리·종류·목표 시간·활성·순서를 관리한다.
 * 유저가 누르면 이 값으로 내 미션을 만들고 바로 시작한다({@code mission.recommended_mission_id} 에 이 ID).
 */
@Entity
@Table(name = "recommended_mission")
@Comment("첫 미션 온보딩 추천 미션 (LUT-782)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendedMission extends LocalDateTimeBaseEntity {

    public static final int TITLE_MAX_LENGTH = 100;
    public static final int ICON_MAX_LENGTH = 16;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "title_ko", nullable = false, length = TITLE_MAX_LENGTH)
    @Comment("미션 이름(한국어, 필수)")
    private String titleKo;

    @Column(name = "title_en", length = TITLE_MAX_LENGTH)
    private String titleEn;

    @Column(name = "title_ja", length = TITLE_MAX_LENGTH)
    private String titleJa;

    @Column(name = "title_ar", length = TITLE_MAX_LENGTH)
    private String titleAr;

    @Column(name = "category_id")
    @Comment("meta_db mission_category.id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 20)
    @Comment("TIMED(타이머) / SIMPLE(체크)")
    private MissionExecutionMode executionMode;

    @Column(name = "target_minutes")
    @Comment("타이머 목표 시간(분) — 체크 미션은 NULL")
    private Integer targetMinutes;

    @Column(name = "icon", nullable = false, length = ICON_MAX_LENGTH)
    @Comment("카드 이모지")
    private String icon;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static RecommendedMission create(
            String titleKo,
            String titleEn,
            String titleJa,
            String titleAr,
            Long categoryId,
            MissionExecutionMode executionMode,
            Integer targetMinutes,
            String icon,
            boolean active,
            int sortOrder) {
        RecommendedMission mission = new RecommendedMission();
        mission.update(
                titleKo,
                titleEn,
                titleJa,
                titleAr,
                categoryId,
                executionMode,
                targetMinutes,
                icon,
                active);
        mission.sortOrder = sortOrder;
        return mission;
    }

    /** 체크 미션은 목표 시간을 저장하지 않는다 (DB 제약과 같다) */
    public void update(
            String titleKo,
            String titleEn,
            String titleJa,
            String titleAr,
            Long categoryId,
            MissionExecutionMode executionMode,
            Integer targetMinutes,
            String icon,
            boolean active) {
        this.titleKo = titleKo;
        this.titleEn = blankToNull(titleEn);
        this.titleJa = blankToNull(titleJa);
        this.titleAr = blankToNull(titleAr);
        this.categoryId = categoryId;
        this.executionMode = executionMode;
        this.targetMinutes = executionMode == MissionExecutionMode.TIMED ? targetMinutes : null;
        this.icon = icon;
        this.active = active;
    }

    public void toggleActive() {
        this.active = !this.active;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 요청 언어 이름 — 비어 있으면 한국어 (미션 이름과 같은 규칙) */
    public String getLocalizedTitle(String locale) {
        return LocaleUtils.getLocalizedText(titleKo, titleEn, titleAr, titleJa, locale);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
