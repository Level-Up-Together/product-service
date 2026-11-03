package io.pinkspider.leveluptogethermvp.missionservice.api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * LUT-577: Idempotency-Key 처리 실패. 플랫폼 {@code CustomException} 은 항상 500 으로 응답하므로, 클라이언트가 재시도 여부를 판단할
 * 수 있게 400(형식 오류)/409(처리 중) 상태를 실어 보내는 전용 예외다. {@link MissionIdempotencyExceptionHandler} 가 처리한다.
 */
@Getter
public class MissionIdempotencyException extends RuntimeException {

    /** 050401 — Idempotency-Key 가 UUID 형식이 아님 (400) */
    public static final String CODE_INVALID_KEY = "050401";

    /** 050402 — 같은 키의 요청이 아직 처리 중 (409, 같은 키로 재시도하면 완료 응답) */
    public static final String CODE_IN_PROGRESS = "050402";

    private final String code;
    private final HttpStatus status;

    private MissionIdempotencyException(String code, String messageKey, HttpStatus status) {
        super(messageKey);
        this.code = code;
        this.status = status;
    }

    public static MissionIdempotencyException invalidKey() {
        return new MissionIdempotencyException(
                CODE_INVALID_KEY, "error.mission.idempotency_key.invalid", HttpStatus.BAD_REQUEST);
    }

    public static MissionIdempotencyException inProgress() {
        return new MissionIdempotencyException(
                CODE_IN_PROGRESS, "error.mission.idempotency_key.in_progress", HttpStatus.CONFLICT);
    }
}
