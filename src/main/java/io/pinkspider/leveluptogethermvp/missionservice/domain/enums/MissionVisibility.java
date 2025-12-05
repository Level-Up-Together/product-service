package io.pinkspider.leveluptogethermvp.missionservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MissionVisibility {

    PUBLIC("public", "공개"),
    PRIVATE("private", "비공개");

    private final String code;
    private final String description;
}
