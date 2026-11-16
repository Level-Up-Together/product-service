package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;
import java.util.List;

/**
 * LUT-775: 어드민 문장 목록 + 빈 시간대. {@code gaps} 는 활성 문장 기준으로 "완료 전"(NOT_DONE·ALL 문장)과 "완료 후"(DONE·ALL
 * 문장) 각각 어떤 문장에도 걸리지 않는 시각이다 — 목록 필터와 상관없이 늘 전체 기준.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record WidgetMessageListResponse(
        List<WidgetMessageResponse> messages, List<WidgetMessageGapResponse> gaps) {

    /** 빈 구간 — 유저 로컬 {@code [start_hour, end_hour)} */
    @JsonNaming(SnakeCaseStrategy.class)
    public record WidgetMessageGapResponse(
            WidgetMessageCondition condition, int startHour, int endHour) {}
}
