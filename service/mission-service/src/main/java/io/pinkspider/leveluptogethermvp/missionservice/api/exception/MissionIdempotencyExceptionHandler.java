package io.pinkspider.leveluptogethermvp.missionservice.api.exception;

import io.pinkspider.global.api.ApiResult;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * LUT-577: {@link MissionIdempotencyException} 을 예외가 지정한 상태(400/409)로 응답한다. 본문은 다른 에러와 같은 {@code
 * ApiResult} (code + 현지화 message). 플랫폼 RestExceptionHandler(HIGHEST_PRECEDENCE+1) 보다 먼저 잡도록
 * HIGHEST_PRECEDENCE 로 둔다 — user-service {@code JwtExceptionHandler} 와 같은 구조.
 */
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class MissionIdempotencyExceptionHandler {

    @Autowired(required = false)
    private MessageSource messageSource;

    @ExceptionHandler(MissionIdempotencyException.class)
    public ResponseEntity<Object> handle(MissionIdempotencyException e) {
        // 재시도 가능한 정상 흐름(409)·클라이언트 입력 오류(400)라 error 가 아닌 info
        log.info("[mission-idempotency] code={} status={}", e.getCode(), e.getStatus().value());
        return ResponseEntity.status(e.getStatus())
                .body(
                        ApiResult.builder()
                                .code(e.getCode())
                                .message(resolveMessage(e.getMessage()))
                                .build());
    }

    private String resolveMessage(String messageOrKey) {
        if (messageSource == null || messageOrKey == null) {
            return messageOrKey;
        }
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(messageOrKey, null, messageOrKey, locale);
    }
}
