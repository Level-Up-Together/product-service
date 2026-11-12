package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-775: 홈 화면 위젯 시간대별 문장. 어드민이 관리하고(LUT-776) 위젯이 활성 문장 전체를 받아 기기에서 고른다(LUT-777). 시간대 규칙은 {@code
 * WidgetHourRange}.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "widget_message")
@Comment("홈 화면 위젯 시간대별 문장")
public class WidgetMessage extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @Column(name = "start_hour", nullable = false)
    @Comment("시작 시각(유저 로컬, 포함)")
    private Integer startHour;

    @Column(name = "end_hour", nullable = false)
    @Comment("끝 시각(유저 로컬, 미포함)")
    private Integer endHour;

    @Enumerated(EnumType.STRING)
    @Column(name = "done_condition", nullable = false, length = 20)
    @Comment("오늘 완료 여부 조건")
    private WidgetMessageCondition condition;

    @Column(name = "text_ko", nullable = false, length = 200)
    @Comment("문장(ko)")
    private String textKo;

    @Column(name = "text_en", length = 200)
    @Comment("문장(en)")
    private String textEn;

    @Column(name = "text_ja", length = 200)
    @Comment("문장(ja)")
    private String textJa;

    @Column(name = "text_ar", length = 200)
    @Comment("문장(ar)")
    private String textAr;

    @Column(name = "active", nullable = false)
    @Comment("노출 여부")
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    @Comment("관리 화면 정렬")
    private int sortOrder;

    /** LUT-797: 위젯 배경 이미지(절대 URL). 없으면 위젯은 하얀 배경 */
    @Column(name = "background_image_url", length = 500)
    @Comment("위젯 배경 이미지 URL(없으면 하얀 배경)")
    private String backgroundImageUrl;

    @Builder
    public WidgetMessage(
            Integer startHour,
            Integer endHour,
            WidgetMessageCondition condition,
            String textKo,
            String textEn,
            String textJa,
            String textAr,
            boolean active,
            int sortOrder,
            String backgroundImageUrl) {
        this.startHour = startHour;
        this.endHour = endHour;
        this.condition = condition;
        this.textKo = textKo;
        this.textEn = textEn;
        this.textJa = textJa;
        this.textAr = textAr;
        this.active = active;
        this.sortOrder = sortOrder;
        this.backgroundImageUrl = backgroundImageUrl;
    }

    public void update(
            Integer startHour,
            Integer endHour,
            WidgetMessageCondition condition,
            String textKo,
            String textEn,
            String textJa,
            String textAr,
            boolean active,
            int sortOrder,
            String backgroundImageUrl) {
        this.startHour = startHour;
        this.endHour = endHour;
        this.condition = condition;
        this.textKo = textKo;
        this.textEn = textEn;
        this.textJa = textJa;
        this.textAr = textAr;
        this.active = active;
        this.sortOrder = sortOrder;
        this.backgroundImageUrl = backgroundImageUrl;
    }

    public void toggleActive() {
        this.active = !this.active;
    }

    /** 요청 언어 문장 — 비어 있으면 en, 그다음 ko */
    public String textFor(String language) {
        String text =
                switch (language == null ? "en" : language) {
                    case "ko" -> textKo;
                    case "ja" -> textJa;
                    case "ar" -> textAr;
                    default -> textEn;
                };
        if (hasText(text)) {
            return text;
        }
        return hasText(textEn) ? textEn : textKo;
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
