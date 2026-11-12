package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums.WidgetMessageCondition;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy.WidgetHourRange;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * LUT-775: 위젯 문장 작성·수정 (어드민). 시간대는 유저 로컬 {@code [start_hour, end_hour)}, start 가 end 보다 크면 자정을 넘는
 * 구간. ko 는 필수, 나머지 언어는 비우면 en → ko 로 대체된다. {@code active}·{@code sort_order} 를 비우면 작성 때는 켬·0, 수정 때는
 * 그대로.
 *
 * <p>LUT-797: {@code background_image_url} 은 업로드 API({@code POST /images})가 준 주소만 받는다. 수정 때 {@code
 * null} 은 "그대로"(이 필드를 모르는 구 어드민 호환), 빈 문자열은 "지움"(하얀 배경). 작성 때는 둘 다 "없음".
 */
@JsonNaming(SnakeCaseStrategy.class)
public record WidgetMessageRequest(
        @NotNull @Min(WidgetHourRange.MIN_START) @Max(WidgetHourRange.MAX_START) Integer startHour,
        @NotNull @Min(WidgetHourRange.MIN_END) @Max(WidgetHourRange.MAX_END) Integer endHour,
        @NotNull WidgetMessageCondition condition,
        @NotBlank @Size(max = WidgetMessageRequest.MAX_TEXT_LENGTH) String textKo,
        @Size(max = WidgetMessageRequest.MAX_TEXT_LENGTH) String textEn,
        @Size(max = WidgetMessageRequest.MAX_TEXT_LENGTH) String textJa,
        @Size(max = WidgetMessageRequest.MAX_TEXT_LENGTH) String textAr,
        Boolean active,
        @Min(0) Integer sortOrder,
        @Size(max = WidgetMessageRequest.MAX_IMAGE_URL_LENGTH) String backgroundImageUrl) {

    /** 위젯이 작아 길게 쓸 일이 없다 — 어드민 미리보기가 3줄 이상을 경고한다(LUT-776) */
    public static final int MAX_TEXT_LENGTH = 120;

    public static final int MAX_IMAGE_URL_LENGTH = 500;

    /** 시작과 끝이 같으면 구간이 비거나 하루 전체인지 모호하다 — 하루 전체는 0~24 */
    @JsonIgnore
    @AssertTrue(message = "start_hour 와 end_hour 는 같을 수 없습니다 (하루 전체는 0~24)")
    public boolean isHourRangeDistinct() {
        return startHour == null || endHour == null || !startHour.equals(endHour);
    }
}
