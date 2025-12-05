package io.pinkspider.leveluptogethermvp.missionservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ParticipantStatus {

    PENDING("pending", "대기중"),
    ACCEPTED("accepted", "승인됨"),
    IN_PROGRESS("in_progress", "진행중"),
    COMPLETED("completed", "완료"),
    FAILED("failed", "실패"),
    WITHDRAWN("withdrawn", "철회");

    private final String code;
    private final String description;
}
