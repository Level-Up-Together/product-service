package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity.WidgetStreakColor;

/**
 * LUT-816: 'N일' 숫자 색 구간 하나 — 연속 실천 일수가 {@code [min_days, max_days]}(양끝 포함) 안이면 숫자를 {@code color} 로
 * 그린다. {@code max_days} 가 null 이면 끝 없음(이상). 어드민 응답과 위젯 요약({@code streak_colors})이 같은 모양을 쓴다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record WidgetStreakColorView(int minDays, Integer maxDays, String color) {

    public static WidgetStreakColorView from(WidgetStreakColor range) {
        return new WidgetStreakColorView(range.getMinDays(), range.getMaxDays(), range.getColor());
    }
}
