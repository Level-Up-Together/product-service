package io.pinkspider.leveluptogethermvp.missionservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MissionType {

    PERSONAL("personal", "개인 미션"),
    GUILD("guild", "길드 미션");

    private final String code;
    private final String description;
}
