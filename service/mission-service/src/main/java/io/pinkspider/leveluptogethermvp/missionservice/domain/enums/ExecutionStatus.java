package io.pinkspider.leveluptogethermvp.missionservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExecutionStatus {

    PENDING("대기중"),
    IN_PROGRESS("진행중"),
    COMPLETED("완료"),
    MISSED("미실행");

    private final String description;
}
