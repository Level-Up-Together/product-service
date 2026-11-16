package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy.WidgetStreakColorRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * LUT-816: 'N일' 숫자 색 구간 저장(어드민) — 보낸 목록으로 통째로 바꾼다. 빈 목록이면 구간을 모두 지운다(모든 N 이 위젯 기본 색). 순서는 상관없다(서버가
 * 시작 일수로 정렬). 항목 형식은 여기서, 겹침·빈 곳은 {@code WidgetStreakColorRule} 이 본다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record WidgetStreakColorRequest(
        @NotNull @Size(max = WidgetStreakColorRule.MAX_RANGES) List<@NotNull @Valid Range> ranges) {

    /** 구간 {@code [min_days, max_days]} 양끝 포함. {@code max_days} 를 비우면 끝 없음(이상) — 마지막 구간만 */
    @JsonNaming(SnakeCaseStrategy.class)
    public record Range(
            @NotNull @Min(0) @Max(WidgetStreakColorRule.MAX_DAYS) Integer minDays,
            @Min(0) @Max(WidgetStreakColorRule.MAX_DAYS) Integer maxDays,
            @NotNull @Pattern(regexp = WidgetStreakColorRule.COLOR_PATTERN) String color) {

        @JsonIgnore
        @AssertTrue(message = "max_days 는 min_days 보다 작을 수 없습니다")
        public boolean isRangeOrdered() {
            return minDays == null || maxDays == null || maxDays >= minDays;
        }
    }
}
