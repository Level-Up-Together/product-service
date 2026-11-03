package io.pinkspider.leveluptogethermvp.towerservice.application.exception;

import io.pinkspider.global.exception.CustomException;
import org.springframework.http.HttpStatus;

/**
 * 탑 도메인 오류의 공통 부모 — 상태 코드를 들고 있어 {@code TowerExceptionHandler} 가 플랫폼 500 대신 그 코드로 응답한다. 코드 앞자리 15 =
 * tower-service, 가운데 두 자리 01 = 층(LUT-629) · 02 = 입장(LUT-630).
 */
public abstract class TowerException extends CustomException {

    private final HttpStatus status;

    protected TowerException(String code, String messageKey, HttpStatus status) {
        super(code, messageKey);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
