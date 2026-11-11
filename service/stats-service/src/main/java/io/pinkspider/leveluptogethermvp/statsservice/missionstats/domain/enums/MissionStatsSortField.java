package io.pinkspider.leveluptogethermvp.statsservice.missionstats.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** LUT-790: 사용자별 수행 표의 정렬 열 — API 값은 응답 필드 이름과 같다 */
@Getter
@RequiredArgsConstructor
public enum MissionStatsSortField {
    COMPLETIONS("completions", false),
    TIMER_MINUTES("timer_minutes", false),
    LAST_COMPLETED_AT("last_completed_at", false),
    NICKNAME("nickname", true),
    LEVEL("level", false);

    private final String param;

    /** 방향을 안 주면 쓰는 기본 — 닉네임만 오름차순, 숫자·날짜는 큰 것부터 */
    private final boolean defaultAscending;

    public static MissionStatsSortField fromParam(String param) {
        for (MissionStatsSortField field : values()) {
            if (field.param.equals(param)) {
                return field;
            }
        }
        return null;
    }
}
