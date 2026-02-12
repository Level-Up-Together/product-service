package io.pinkspider.global.event;

/**
 * 길드 멤버 가입 시 채팅방에 알림 메시지를 보내기 위한 이벤트
 */
public record GuildMemberJoinedChatNotifyEvent(
    Long guildId,
    String memberNickname
) {}
