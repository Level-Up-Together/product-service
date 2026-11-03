package io.pinkspider.leveluptogethermvp.towerservice.api.exception;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * LUT-629: 탑 층 오류를 예외가 가진 상태 코드(400·404)로 응답한다. 플랫폼 {@code RestExceptionHandler} 는 모든 {@code
 * CustomException} 을 500 으로 바꾸므로 LUT-616 과 같은 구조로 towerservice 패키지 한정 {@code HIGHEST_PRECEDENCE}
 * 어드바이스를 둔다.
 */
@RestControllerAdvice(basePackages = "io.pinkspider.leveluptogethermvp.towerservice")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TowerExceptionHandler {

    @Nullable private final MessageSource messageSource;

    public TowerExceptionHandler(@Nullable MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(TowerFloorException.class)
    public ResponseEntity<Object> handle(TowerFloorException e) {
        log.info("[tower] 층 요청 거절: status={} code={}", e.getStatus().value(), e.getCode());
        return ResponseEntity.status(e.getStatus())
                .body(
                        ApiResult.builder()
                                .code(e.getCode())
                                .message(resolveMessage(e.getMessage()))
                                .build());
    }

    private String resolveMessage(String key) {
        if (messageSource == null) {
            return key;
        }
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
