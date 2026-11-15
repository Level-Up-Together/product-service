package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.policy.WidgetStreakColorRule.Violation;
import lombok.Getter;

/**
 * LUT-816: 'N일' 숫자 색 구간 저장 거절 — {@code WidgetMessageExceptionHandler} 가 400 으로 돌려준다(플랫폼 {@code
 * CustomException} 은 늘 500 이라 쓰지 않는다). 겹침 {@code 080206}, 빈 곳 {@code 080207}.
 */
@Getter
public class WidgetStreakColorException extends RuntimeException {

    public static final String CODE_OVERLAP = "080206";
    public static final String CODE_GAP = "080207";

    private final String code;
    private final String messageKey;

    private WidgetStreakColorException(String code, String messageKey) {
        super(messageKey);
        this.code = code;
        this.messageKey = messageKey;
    }

    public static WidgetStreakColorException of(Violation violation) {
        return violation == Violation.OVERLAP
                ? new WidgetStreakColorException(
                        CODE_OVERLAP, "error.widget_message.streak_color_overlap")
                : new WidgetStreakColorException(CODE_GAP, "error.widget_message.streak_color_gap");
    }
}
