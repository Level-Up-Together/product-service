package io.pinkspider.leveluptogethermvp.towerservice.application.exception;

import io.pinkspider.global.exception.CustomException;
import org.springframework.http.HttpStatus;

/**
 * 탑 도메인 오류의 공통 부모 — 상태 코드를 들고 있어 {@code TowerExceptionHandler} 가 플랫폼 500 대신 그 코드로 응답한다. 코드 앞자리 15 =
 * tower-service, 가운데 두 자리 01 = 층(LUT-629) · 02 = 입장(LUT-630).
 */
public abstract class TowerException extends CustomException {

    private final HttpStatus status;
    private final Object[] messageArgs;
    private final Object detail;

    protected TowerException(String code, String messageKey, HttpStatus status) {
        this(code, messageKey, status, null, null);
    }

    /** LUT-738: 문구 인자({0})와 응답 {@code value} 로 내려 줄 세부 값(예: 남은 초) */
    protected TowerException(
            String code,
            String messageKey,
            HttpStatus status,
            Object[] messageArgs,
            Object detail) {
        super(code, messageKey);
        this.status = status;
        this.messageArgs = messageArgs;
        this.detail = detail;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }

    public Object getDetail() {
        return detail;
    }
}
