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

    /**
     * 상태 전이 유효성 검증
     *
     * <p>PENDING → IN_PROGRESS, MISSED IN_PROGRESS → COMPLETED, PENDING(취소), MISSED COMPLETED →
     * (terminal, resetToPending은 별도 메서드로 처리) MISSED → (terminal)
     */
    public boolean canTransitTo(ExecutionStatus target) {
        return switch (this) {
            case PENDING -> target == IN_PROGRESS || target == MISSED;
            case IN_PROGRESS -> target == COMPLETED || target == PENDING || target == MISSED;
            case COMPLETED, MISSED -> false;
        };
    }
}
