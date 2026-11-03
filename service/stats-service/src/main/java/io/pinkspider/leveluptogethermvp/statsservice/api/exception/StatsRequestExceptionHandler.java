package io.pinkspider.leveluptogethermvp.statsservice.api.exception;

import io.pinkspider.global.api.ApiResult;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * LUT-616: stats-service 내부 API(KPI·활동 flush)의 요청 파라미터 오류를 400 으로 돌려준다.
 *
 * <p>{@code @DateTimeFormat} 변환 실패({@link MethodArgumentTypeMismatchException})와 필수 파라미터 누락은 Spring
 * 기본 처리라면 400 이지만, 플랫폼 {@code RestExceptionHandler} 의 {@code @ExceptionHandler(Exception.class)} 가
 * 먼저 잡아 500 {@code 000500} 으로 바꾼다. 그래서 LUT-577 {@code MissionIdempotencyExceptionHandler} 와 같은 구조로
 * {@code HIGHEST_PRECEDENCE} 어드바이스를 두되, 범위는 statsservice 패키지의 컨트롤러로 한정한다(다른 서비스 응답은 그대로).
 */
@RestControllerAdvice(basePackages = "io.pinkspider.leveluptogethermvp.statsservice")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class StatsRequestExceptionHandler {

    /** 파라미터 형식 오류(날짜 yyyy-MM-dd · 월 yyyy-MM · enum 허용값 밖) */
    public static final String CODE_INVALID_PARAM = "100101";

    /** 필수 파라미터 누락 */
    public static final String CODE_MISSING_PARAM = "100102";

    static final String KEY_INVALID_PARAM = "error.stats.param.invalid";
    static final String KEY_MISSING_PARAM = "error.stats.param.missing";

    @Nullable private final MessageSource messageSource;

    public StatsRequestExceptionHandler(@Nullable MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String expected = describeExpectedType(e.getRequiredType());
        // 클라이언트 입력 오류라 error 가 아닌 info
        log.info(
                "[stats] 파라미터 형식 오류 name={} value={} expected={}",
                e.getName(),
                e.getValue(),
                expected);
        return badRequest(CODE_INVALID_PARAM, KEY_INVALID_PARAM, e.getName(), expected);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissing(MissingServletRequestParameterException e) {
        log.info("[stats] 필수 파라미터 누락 name={}", e.getParameterName());
        return badRequest(CODE_MISSING_PARAM, KEY_MISSING_PARAM, e.getParameterName());
    }

    /** 기대 형식을 사람이 읽을 수 있게 — 날짜/월은 패턴, enum 은 허용값 목록, 그 외는 타입 이름 */
    static String describeExpectedType(@Nullable Class<?> type) {
        if (type == null) {
            return "-";
        }
        if (LocalDate.class.equals(type)) {
            return "yyyy-MM-dd";
        }
        if (YearMonth.class.equals(type)) {
            return "yyyy-MM";
        }
        if (type.isEnum()) {
            return Arrays.stream(type.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
        }
        return type.getSimpleName();
    }

    private ResponseEntity<Object> badRequest(String code, String messageKey, Object... args) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResult.builder()
                                .code(code)
                                .message(resolveMessage(messageKey, args))
                                .build());
    }

    private String resolveMessage(String messageKey, Object... args) {
        if (messageSource == null) {
            return messageKey;
        }
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(messageKey, args, messageKey, locale);
    }
}
