package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * LUT-816: 홈 화면 위젯 'N일' 숫자 색 구간. 구간 한 벌을 모든 위젯 문장에 쓴다 — 어드민이 통째로 바꾸고, 위젯은 연속 실천 일수가 드는 구간의 색으로 숫자를
 * 그린다. 규칙은 {@code WidgetStreakColorRule}.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "widget_streak_color")
@Comment("홈 화면 위젯 연속 실천 일수 숫자 색 구간")
public class WidgetStreakColor extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @Column(name = "min_days", nullable = false)
    @Comment("구간 시작 일수(포함)")
    private int minDays;

    @Column(name = "max_days")
    @Comment("구간 끝 일수(포함). NULL 이면 끝 없음")
    private Integer maxDays;

    @Column(name = "color", nullable = false, length = 7)
    @Comment("숫자 색(#rrggbb)")
    private String color;

    @Builder
    public WidgetStreakColor(int minDays, Integer maxDays, String color) {
        this.minDays = minDays;
        this.maxDays = maxDays;
        this.color = color;
    }
}
