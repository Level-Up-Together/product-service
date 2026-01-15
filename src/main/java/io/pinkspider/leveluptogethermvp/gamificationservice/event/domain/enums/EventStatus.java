package io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventStatus {
    SCHEDULED("예정"),
    IN_PROGRESS("진행중"),
    ENDED("종료");

    private final String displayName;
}
