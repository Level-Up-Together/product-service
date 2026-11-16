package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.enums;

/** LUT-775: 위젯 문장을 보여 줄 조건 — 유저가 오늘(유저 로컬) 미션을 하나라도 완료했는가 */
public enum WidgetMessageCondition {
    /** 오늘 완료 전 */
    NOT_DONE,
    /** 오늘 완료 후 */
    DONE,
    /** 완료 여부와 상관없이 */
    ALL;

    public boolean matches(boolean completedToday) {
        return switch (this) {
            case NOT_DONE -> !completedToday;
            case DONE -> completedToday;
            case ALL -> true;
        };
    }
}
