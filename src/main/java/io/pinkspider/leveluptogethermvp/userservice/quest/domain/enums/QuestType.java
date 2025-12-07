package io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuestType {
    DAILY("일일"),
    WEEKLY("주간"),
    SPECIAL("특별");

    private final String displayName;
}
