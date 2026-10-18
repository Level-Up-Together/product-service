package io.pinkspider.leveluptogethermvp.missionservice.domain.enums;

import io.pinkspider.leveluptogethermvp.missionservice.domain.policy.MissionExpPolicy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MissionExecutionMode {
    TIMED("timed", "시간 측정"),
    SIMPLE("simple", "수행 여부");

    private final String code;
    private final String description;

    /** SIMPLE 모드 고정 경험치 (LUT-568: 공식은 {@link MissionExpPolicy}) */
    public static final int SIMPLE_EXP = MissionExpPolicy.SIMPLE_EXP;

    /** SIMPLE 모드 하루 즉시완료 제한 횟수 */
    public static final int SIMPLE_DAILY_LIMIT = MissionExpPolicy.SIMPLE_DAILY_LIMIT;
}
