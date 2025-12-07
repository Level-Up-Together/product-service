package io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuestActionType {
    // 미션 관련
    COMPLETE_MISSION("미션 완료", 1),
    COMPLETE_MISSIONS("미션 N개 완료", 3),
    COMPLETE_GUILD_MISSION("길드 미션 완료", 1),

    // 출석 관련
    CHECK_IN("출석 체크", 1),
    CONSECUTIVE_ATTENDANCE("연속 출석", 3),

    // 소셜 관련
    JOIN_GUILD("길드 가입", 1),
    INVITE_MEMBER("멤버 초대", 1),

    // 레벨 관련
    GAIN_EXP("경험치 획득", 100),
    LEVEL_UP("레벨업", 1),

    // 일반
    LOGIN("로그인", 1),
    PROFILE_UPDATE("프로필 수정", 1);

    private final String displayName;
    private final int defaultRequiredCount;
}
