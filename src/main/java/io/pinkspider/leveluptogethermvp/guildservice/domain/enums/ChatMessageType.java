package io.pinkspider.leveluptogethermvp.guildservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatMessageType {
    TEXT("텍스트"),
    IMAGE("이미지"),
    SYSTEM("시스템"),
    ACHIEVEMENT("업적"),
    LEVEL_UP("레벨업"),
    MISSION_COMPLETE("미션완료"),
    MEMBER_JOIN("멤버가입"),
    MEMBER_LEAVE("멤버탈퇴");

    private final String displayName;

    public boolean isSystemMessage() {
        return this == SYSTEM || this == ACHIEVEMENT || this == LEVEL_UP ||
               this == MISSION_COMPLETE || this == MEMBER_JOIN || this == MEMBER_LEAVE;
    }
}
