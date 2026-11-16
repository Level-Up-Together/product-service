package io.pinkspider.leveluptogethermvp.bffservice.api.exception;

import io.pinkspider.global.api.ApiResult;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * LUT-804: {@link SeasonNotFoundException} 을 404 로 응답한다. 본문은 다른 에러와 같은 {@code ApiResult} (code +
 * 현지화 message). 플랫폼 RestExceptionHandler(HIGHEST_PRECEDENCE+1) 보다 먼저 잡도록 HIGHEST_PRECEDENCE 로 둔다 —
 * 미션 {@code MissionIdempotencyExceptionHandler} 와 같은 구조.
 */
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class BffSeasonExceptionHandler {

    @Autowired(required = false)
    private MessageSource messageSource;

    @ExceptionHandler(SeasonNotFoundException.class)
    public ResponseEntity<Object> handle(SeasonNotFoundException e) {
        // 시즌이 없는 것은 정상 상황이다 — error 로그를 남기지 않는다
        log.debug("[bff-season] code={}", e.getCode());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
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
