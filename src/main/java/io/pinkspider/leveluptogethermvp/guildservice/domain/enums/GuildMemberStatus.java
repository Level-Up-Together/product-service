package io.pinkspider.leveluptogethermvp.guildservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GuildMemberStatus {
    ACTIVE("활성"),
    LEFT("탈퇴"),
    KICKED("추방");

    private final String description;
}
