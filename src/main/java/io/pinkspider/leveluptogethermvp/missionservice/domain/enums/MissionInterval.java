package io.pinkspider.leveluptogethermvp.missionservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MissionInterval {

    DAILY("매일", 1),
    EVERY_OTHER_DAY("격일", 2),
    EVERY_THREE_DAYS("3일마다", 3),
    WEEKLY("매주", 7),
    BIWEEKLY("격주", 14),
    MONTHLY("매월", 30);

    private final String description;
    private final int days;
}
