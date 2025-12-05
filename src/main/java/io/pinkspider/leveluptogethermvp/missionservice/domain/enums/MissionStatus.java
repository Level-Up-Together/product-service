package io.pinkspider.leveluptogethermvp.missionservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MissionStatus {

    DRAFT("draft", "작성중"),
    OPEN("open", "모집중"),
    IN_PROGRESS("in_progress", "진행중"),
    COMPLETED("completed", "완료"),
    CANCELLED("cancelled", "취소됨");

    private final String code;
    private final String description;
}
