package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity.WidgetMessage;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;
import java.time.LocalDateTime;

/** LUT-775: 위젯 문장 (어드민 응답 — 4개 언어 원문) */
@JsonNaming(SnakeCaseStrategy.class)
public record WidgetMessageResponse(
        Long id,
        Integer startHour,
        Integer endHour,
        WidgetMessageCondition condition,
        String textKo,
        String textEn,
        String textJa,
        String textAr,
        boolean active,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {

    public static WidgetMessageResponse from(WidgetMessage message) {
        return new WidgetMessageResponse(
                message.getId(),
                message.getStartHour(),
                message.getEndHour(),
                message.getCondition(),
                message.getTextKo(),
                message.getTextEn(),
                message.getTextJa(),
                message.getTextAr(),
                message.isActive(),
                message.getSortOrder(),
                message.getCreatedAt(),
                message.getModifiedAt());
    }
}
