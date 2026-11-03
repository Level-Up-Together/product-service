package io.pinkspider.global.moderation.exception;

import io.pinkspider.global.exception.CustomException;
import org.springframework.http.HttpStatus;

/**
 * 이미지 검수 결과로 업로드를 거절할 때 던지는 예외 (LUT-714).
 *
 * <p>예전엔 {@link CustomException} 을 그대로 던져 플랫폼 {@code RestExceptionHandler} 가 500 으로 응답했다 — 사용자
 * 실수(부적절한 사진)가 서버 장애(5xx)로 집계되고 ERROR 스택이 쌓였다. 이 예외는 {@code ImageModerationExceptionHandler} 가 받아
 * 상황에 맞는 상태 코드로 응답한다: 부적절한 이미지 {@code 000010} → 400, 검수 불가(fail-closed) {@code 000011} → 503. 본문
 * {@code code}·{@code message}(i18n 키) 는 예전과 같아 웹·앱은 바꿀 것이 없다.
 */
public class ImageModerationException extends CustomException {

    public static final String BLOCKED_CODE = "000010";
    public static final String BLOCKED_MESSAGE_KEY = "error.moderation.inappropriate_image";
    public static final String UNAVAILABLE_CODE = "000011";
    public static final String UNAVAILABLE_MESSAGE_KEY = "error.moderation.unavailable";

    private final HttpStatus status;

    private ImageModerationException(String code, String messageKey, HttpStatus status) {
        super(code, messageKey);
        this.status = status;
    }

    /** 부적절한 이미지(차단) — 400 Bad Request. 사용자가 다른 사진을 고르면 되는 입력 오류다. */
    public static ImageModerationException blocked() {
        return new ImageModerationException(
                BLOCKED_CODE, BLOCKED_MESSAGE_KEY, HttpStatus.BAD_REQUEST);
    }

    /** 검수 불가로 차단(fail-closed) — 503 Service Unavailable. 잠시 뒤 다시 시도하면 될 일시 장애다. */
    public static ImageModerationException unavailable() {
        return new ImageModerationException(
                UNAVAILABLE_CODE, UNAVAILABLE_MESSAGE_KEY, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
