package io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeasonStatus {
    UPCOMING("예정"),
    ACTIVE("진행중"),
    ENDED("종료");

    private final String description;
}
