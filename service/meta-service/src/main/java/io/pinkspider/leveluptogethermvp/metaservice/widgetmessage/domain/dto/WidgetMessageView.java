package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity.WidgetMessage;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;

/** LUT-775: 위젯에 내려 주는 문장 — 요청 언어 한 개(비면 en → ko). {@code {streak}} 는 위젯이 바꾼다 */
@JsonNaming(SnakeCaseStrategy.class)
public record WidgetMessageView(
        Long id, int startHour, int endHour, WidgetMessageCondition condition, String text) {

    public static WidgetMessageView of(WidgetMessage message, String language) {
        return new WidgetMessageView(
                message.getId(),
                message.getStartHour(),
                message.getEndHour(),
                message.getCondition(),
                message.textFor(language));
    }
}
