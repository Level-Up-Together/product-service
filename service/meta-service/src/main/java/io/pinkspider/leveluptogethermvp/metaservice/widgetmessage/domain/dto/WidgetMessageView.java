package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity.WidgetMessage;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;

/**
 * LUT-775: 위젯에 내려 주는 문장 — 요청 언어 한 개(비면 en → ko). {@code {streak}} 는 위젯이 바꾼다.
 *
 * <p>LUT-797: {@code background_image_url} — 그 문장을 보여 줄 때 위젯 배경에 깔 이미지(절대 URL). 없으면 null 이고 위젯은 하얀
 * 배경을 쓴다. 추가 필드라 이 값을 모르는 구앱 위젯은 무시한다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record WidgetMessageView(
        Long id,
        int startHour,
        int endHour,
        WidgetMessageCondition condition,
        String text,
        String backgroundImageUrl) {

    public static WidgetMessageView of(WidgetMessage message, String language) {
        return new WidgetMessageView(
                message.getId(),
                message.getStartHour(),
                message.getEndHour(),
                message.getCondition(),
                message.textFor(language),
                message.getBackgroundImageUrl());
    }
}
