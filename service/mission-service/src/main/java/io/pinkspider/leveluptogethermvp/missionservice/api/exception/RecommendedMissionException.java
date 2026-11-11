package io.pinkspider.leveluptogethermvp.missionservice.api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * LUT-782: 추천 미션 요청 실패 — 플랫폼 {@code CustomException} 은 항상 500 이라, 없음(404)·입력 오류(400)를 상태로 실어 보낸다.
 * {@link MissionIdempotencyExceptionHandler} 가 같은 방식으로 응답한다.
 */
@Getter
public class RecommendedMissionException extends RuntimeException {

    /** 050501 — 추천 미션이 없거나 꺼져 있음 (404) */
    public static final String CODE_NOT_FOUND = "050501";

    /** 050502 — 어드민 입력 오류 (400) */
    public static final String CODE_INVALID = "050502";

    private final String code;
    private final HttpStatus status;

    private RecommendedMissionException(String code, String messageKey, HttpStatus status) {
        super(messageKey);
        this.code = code;
        this.status = status;
    }

    public static RecommendedMissionException notFound() {
        return new RecommendedMissionException(
                CODE_NOT_FOUND, "error.mission.recommended.not_found", HttpStatus.NOT_FOUND);
    }

    public static RecommendedMissionException targetMinutesRequired() {
        return invalid("error.mission.recommended.target_minutes_required");
    }

    public static RecommendedMissionException invalidCategory() {
        return invalid("error.mission.recommended.invalid_category");
    }

    public static RecommendedMissionException invalidReorder() {
        return invalid("error.mission.recommended.invalid_reorder");
    }

    private static RecommendedMissionException invalid(String messageKey) {
        return new RecommendedMissionException(CODE_INVALID, messageKey, HttpStatus.BAD_REQUEST);
    }
}
