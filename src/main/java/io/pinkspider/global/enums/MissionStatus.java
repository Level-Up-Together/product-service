package io.pinkspider.global.enums;

import java.util.List;
import java.util.Set;
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

    private Set<MissionStatus> allowedTransitions;

    static {
        DRAFT.allowedTransitions = Set.of(OPEN, CANCELLED);
        OPEN.allowedTransitions = Set.of(IN_PROGRESS, CANCELLED);
        IN_PROGRESS.allowedTransitions = Set.of(COMPLETED, CANCELLED);
        COMPLETED.allowedTransitions = Set.of();
        CANCELLED.allowedTransitions = Set.of();
    }

    public boolean canTransitionTo(MissionStatus target) {
        return allowedTransitions.contains(target);
    }

    public boolean isModifiable() {
        return this == DRAFT;
    }

    public boolean isJoinable() {
        return this == OPEN;
    }

    public boolean isDeletable() {
        return this != IN_PROGRESS;
    }

    public boolean isActive() {
        return this == DRAFT || this == OPEN || this == IN_PROGRESS;
    }

    public static List<MissionStatus> activeStatuses() {
        return List.of(DRAFT, OPEN, IN_PROGRESS);
    }
}
