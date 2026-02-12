package io.pinkspider.leveluptogethermvp.missionservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MissionInterval {

    DAILY("매일", 1),
    WEEKLY("매주", 7),
    MONTHLY("매월", 30);

    private final String description;
    private final int days;
}
