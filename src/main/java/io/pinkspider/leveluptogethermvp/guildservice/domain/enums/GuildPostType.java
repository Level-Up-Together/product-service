package io.pinkspider.leveluptogethermvp.guildservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GuildPostType {
    NOTICE("공지"),
    NORMAL("일반");

    private final String description;
}
