package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums;

/** LUT-779: 가입 첫날(가입 후 24시간) 행동으로 나눈 그룹 */
public enum FirstDayGroup {
    /** 첫날 미션을 완료하지 않음 (상호작용은 상관없음) */
    NOT_COMPLETED,
    /** 첫날 미션 완료 + 다른 사람과 상호작용 없음 */
    SOLO,
    /** 첫날 미션 완료 + 다른 사람과 상호작용 있음 */
    SOCIAL;

    public static FirstDayGroup of(boolean completed, boolean interacted) {
        if (!completed) {
            return NOT_COMPLETED;
        }
        return interacted ? SOCIAL : SOLO;
    }
}
