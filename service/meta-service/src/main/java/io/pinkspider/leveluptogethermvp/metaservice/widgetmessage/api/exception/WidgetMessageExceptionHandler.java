package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.api.exception;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application.WidgetMessageNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * LUT-775: 위젯 문장 API 의 "없음" 을 404, 파라미터 형식 오류를 400 으로 돌려준다. 플랫폼 {@code CustomException} 은 늘 500
 * 이라(LUT-616 과 같은 이유) widgetmessage 패키지 컨트롤러로 범위를 한정한 {@code HIGHEST_PRECEDENCE} 어드바이스를 둔다. 입력 오류는
 * Bean Validation → 플랫폼 400.
 */
@RestControllerAdvice(basePackages = "io.pinkspider.leveluptogethermvp.metaservice.widgetmessage")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class WidgetMessageExceptionHandler {

    public static final String CODE_NOT_FOUND = "080201";

    /** 조건 필터 등 파라미터가 허용값 밖 */
    public static final String CODE_INVALID_PARAM = "080202";

    static final String KEY_NOT_FOUND = "error.widget_message.not_found";
    static final String KEY_INVALID_PARAM = "error.widget_message.invalid_param";

    @Nullable private final MessageSource messageSource;

    public WidgetMessageExceptionHandler(@Nullable MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(WidgetMessageNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(WidgetMessageNotFoundException e) {
        log.info("[widget-message] {}", e.getMessage());
        return respond(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, KEY_NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.info("[widget-message] 파라미터 형식 오류 name={} value={}", e.getName(), e.getValue());
        return respond(HttpStatus.BAD_REQUEST, CODE_INVALID_PARAM, KEY_INVALID_PARAM, e.getName());
    }

    private ResponseEntity<Object> respond(
            HttpStatus status, String code, String messageKey, Object... args) {
        String message =
                messageSource == null
                        ? messageKey
                        : messageSource.getMessage(
                                messageKey, args, messageKey, LocaleContextHolder.getLocale());
        return ResponseEntity.status(status)
                .body(ApiResult.builder().code(code).message(message).build());
    }
}
