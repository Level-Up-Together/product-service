package io.pinkspider.leveluptogethermvp.chatservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatMessageType {
    TEXT("텍스트"),
    IMAGE("이미지"),
    SYSTEM_JOIN("시스템-가입"),
    SYSTEM_LEAVE("시스템-탈퇴"),
    SYSTEM_KICK("시스템-추방"),
    SYSTEM_ACHIEVEMENT("시스템-업적"),
    SYSTEM_MISSION("시스템-미션"),
    SYSTEM_LEVEL_UP("시스템-레벨업"),
    SYSTEM_ANNOUNCEMENT("시스템-공지");

    private final String displayName;

    public boolean isSystemMessage() {
        return this != TEXT && this != IMAGE;
    }
}
