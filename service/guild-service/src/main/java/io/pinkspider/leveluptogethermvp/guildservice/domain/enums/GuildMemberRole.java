package io.pinkspider.leveluptogethermvp.guildservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GuildMemberRole {
    MASTER("길드 마스터"),
    SUB_MASTER("부길드 마스터"),
    MEMBER("길드원");

    private final String description;
}
