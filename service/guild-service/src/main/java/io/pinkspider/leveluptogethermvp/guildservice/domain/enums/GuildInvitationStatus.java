package io.pinkspider.leveluptogethermvp.guildservice.domain.enums;

/**
 * 길드 초대 상태
 */
public enum GuildInvitationStatus {
    PENDING,    // 대기중
    ACCEPTED,   // 수락됨
    REJECTED,   // 거절됨
    CANCELLED,  // 취소됨
    EXPIRED     // 만료됨
}