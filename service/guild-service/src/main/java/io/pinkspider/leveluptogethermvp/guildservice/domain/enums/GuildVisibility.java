package io.pinkspider.leveluptogethermvp.guildservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GuildVisibility {
    PUBLIC("공개"),
    PRIVATE("비공개");

    private final String description;
}
