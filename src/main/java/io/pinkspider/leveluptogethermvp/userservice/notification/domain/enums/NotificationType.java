package io.pinkspider.leveluptogethermvp.userservice.notification.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    // 시스템
    SYSTEM("시스템", "SYSTEM"),
    ANNOUNCEMENT("공지사항", "SYSTEM"),

    // 미션 관련
    MISSION_REMINDER("미션 리마인더", "MISSION"),
    MISSION_COMPLETED("미션 완료", "MISSION"),
    MISSION_FULL_COMPLETED("미션 전체 완료", "MISSION"),

    // 업적 관련
    ACHIEVEMENT_UNLOCKED("업적 달성", "ACHIEVEMENT"),
    TITLE_ACQUIRED("칭호 획득", "ACHIEVEMENT"),

    // 레벨 관련
    LEVEL_UP("레벨업", "LEVEL"),
    EXP_GAINED("경험치 획득", "LEVEL"),

    // 길드 관련
    GUILD_INVITE("길드 초대", "GUILD"),
    GUILD_JOIN_REQUEST("길드 가입 신청", "GUILD"),
    GUILD_JOIN_APPROVED("길드 가입 승인", "GUILD"),
    GUILD_JOIN_REJECTED("길드 가입 거절", "GUILD"),
    GUILD_LEVEL_UP("길드 레벨업", "GUILD"),

    // 퀘스트 관련
    QUEST_COMPLETED("퀘스트 완료", "QUEST"),
    QUEST_REWARD_AVAILABLE("보상 수령 가능", "QUEST"),
    DAILY_QUEST_RESET("일일 퀘스트 초기화", "QUEST"),
    WEEKLY_QUEST_RESET("주간 퀘스트 초기화", "QUEST"),

    // 출석 관련
    ATTENDANCE_REMINDER("출석 리마인더", "ATTENDANCE"),
    ATTENDANCE_STREAK("연속 출석 보너스", "ATTENDANCE"),

    // 랭킹 관련
    RANKING_CHANGED("랭킹 변동", "RANKING");

    private final String displayName;
    private final String category;
}
