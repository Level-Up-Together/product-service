package io.pinkspider.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExpSourceType {
    MISSION_EXECUTION("미션 수행 완료"),
    MISSION_FULL_COMPLETION("미션 전체 완료 보너스"),
    ACHIEVEMENT("업적 보상"),
    BONUS("보너스"),
    ADMIN_GRANT("관리자 지급"),
    EVENT("이벤트");

    private final String description;
}
