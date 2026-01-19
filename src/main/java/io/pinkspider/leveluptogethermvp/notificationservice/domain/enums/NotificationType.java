package io.pinkspider.leveluptogethermvp.notificationservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    // 시스템
    SYSTEM("시스템", "SYSTEM"),
    ANNOUNCEMENT("공지사항", "SYSTEM"),
    LEVEL_UP("레벨 업", "SYSTEM"),

    // 친구 관련
    FRIEND_REQUEST("친구 요청", "FRIEND"),
    FRIEND_ACCEPTED("친구 수락", "FRIEND"),
    FRIEND_REJECTED("친구 거절", "FRIEND"),

    // 길드 관련
    GUILD_INVITE("길드 초대", "GUILD"),
    GUILD_JOIN_REQUEST("길드 가입 신청", "GUILD"),
    GUILD_JOIN_APPROVED("길드 가입 승인", "GUILD"),
    GUILD_JOIN_REJECTED("길드 가입 거절", "GUILD"),
    GUILD_MISSION_ARRIVED("길드 미션 도착", "GUILD"),
    GUILD_BULLETIN("길드 공지사항", "GUILD"),
    GUILD_CHAT("길드 채팅", "GUILD"),

    // 소셜 관련 (댓글)
    COMMENT_ON_MY_FEED("내 글에 댓글", "SOCIAL"),

    // 미션 관련
    MISSION("미션", "MISSION"),
    MISSION_COMPLETED("미션 완료", "MISSION"),

    // 업적/칭호 관련
    ACHIEVEMENT("업적", "ACHIEVEMENT"),
    ACHIEVEMENT_COMPLETED("업적 달성", "ACHIEVEMENT"),
    TITLE_ACQUIRED("칭호 획득", "ACHIEVEMENT");

    private final String displayName;
    private final String category;
}
