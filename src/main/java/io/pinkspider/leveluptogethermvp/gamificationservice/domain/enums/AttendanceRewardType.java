package io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AttendanceRewardType {
    DAILY("일일 출석", 10),
    CONSECUTIVE_3("3일 연속 출석", 20),
    CONSECUTIVE_7("7일 연속 출석", 50),
    CONSECUTIVE_14("14일 연속 출석", 100),
    CONSECUTIVE_30("30일 연속 출석", 200),
    MONTHLY_COMPLETE("월간 개근", 500),
    SPECIAL_DAY("특별한 날", 100);

    private final String displayName;
    private final int defaultExp;
}
