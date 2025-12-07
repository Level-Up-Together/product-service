package io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuestCategory {
    MISSION("미션"),
    ATTENDANCE("출석"),
    SOCIAL("소셜"),
    GUILD("길드"),
    LEVEL("레벨"),
    GENERAL("일반");

    private final String displayName;
}
