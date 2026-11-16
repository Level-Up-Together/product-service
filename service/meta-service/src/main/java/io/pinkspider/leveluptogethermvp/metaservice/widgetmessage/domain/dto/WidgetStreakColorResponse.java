package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** LUT-816: 어드민 'N일' 숫자 색 구간 목록(시작 일수 순). 비어 있으면 모든 N 이 위젯 기본 색 */
@JsonNaming(SnakeCaseStrategy.class)
public record WidgetStreakColorResponse(List<WidgetStreakColorView> ranges) {}
