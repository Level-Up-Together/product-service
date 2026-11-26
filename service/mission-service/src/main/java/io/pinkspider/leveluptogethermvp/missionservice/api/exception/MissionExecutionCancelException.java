package io.pinkspider.leveluptogethermvp.missionservice.api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * LUT-860: 완료한 미션 수행 취소 실패 — 플랫폼 {@code CustomException} 은 항상 500 이라, 없음(404)·입력 오류(400)를 상태로 실어
 * 보낸다. {@link MissionIdempotencyExceptionHandler} 가 같은 방식으로 응답한다.
 */
@Getter
public class MissionExecutionCancelException extends RuntimeException {

    /** 050701 — 그 날짜의 수행 기록이 없음 (404) */
    public static final String CODE_NOT_FOUND = "050701";

    /** 050702 — 완료한 수행이 아님 — 대기·진행 중·미실행 (400). 진행 중인 수행은 기존 진행 취소 API 를 쓴다 */
    public static final String CODE_NOT_COMPLETED = "050702";

    /** 050703 — 오늘 완료한 수행이 아님 (400) */
    public static final String CODE_NOT_TODAY = "050703";

    private final String code;
    private final HttpStatus status;

    private MissionExecutionCancelException(String code, String messageKey, HttpStatus status) {
        super(messageKey);
        this.code = code;
        this.status = status;
    }

    public static MissionExecutionCancelException notFound() {
        return new MissionExecutionCancelException(
                CODE_NOT_FOUND, "error.mission.cancel.not_found", HttpStatus.NOT_FOUND);
    }

    public static MissionExecutionCancelException notCompleted() {
        return new MissionExecutionCancelException(
                CODE_NOT_COMPLETED, "error.mission.cancel.not_completed", HttpStatus.BAD_REQUEST);
    }

    public static MissionExecutionCancelException notToday() {
        return new MissionExecutionCancelException(
                CODE_NOT_TODAY, "error.mission.cancel.not_today", HttpStatus.BAD_REQUEST);
    }
}
