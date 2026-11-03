package io.pinkspider.leveluptogethermvp.statsservice.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.enums.RetentionSource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@DisplayName("StatsRequestExceptionHandler (LUT-616 내부 API 파라미터 오류 400)")
class StatsRequestExceptionHandlerTest {

    @SuppressWarnings("unused")
    private void sample(LocalDate from) {}

    private MethodArgumentTypeMismatchException mismatch(Object value, Class<?> requiredType)
            throws NoSuchMethodException {
        MethodParameter param =
                new MethodParameter(getClass().getDeclaredMethod("sample", LocalDate.class), 0);
        return new MethodArgumentTypeMismatchException(value, requiredType, "from", param, null);
    }

    @Test
    @DisplayName("형식 오류: 400 + 100101, MessageSource 가 없으면 메시지 키를 그대로 돌려준다")
    void typeMismatchWithoutMessageSource() throws Exception {
        StatsRequestExceptionHandler handler = new StatsRequestExceptionHandler(null);

        ResponseEntity<Object> response =
                handler.handleTypeMismatch(mismatch("20261020", LocalDate.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResult<?> body = (ApiResult<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("100101");
        assertThat(body.getMessage()).isEqualTo("error.stats.param.invalid");
    }

    @Test
    @DisplayName("형식 오류: MessageSource 가 있으면 파라미터 이름·기대 형식을 채운 현지화 메시지")
    void typeMismatchWithMessageSource() throws Exception {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("error.stats.param.invalid", Locale.KOREAN, "{0} 형식 오류 (기대: {1})");
        LocaleContextHolder.setLocale(Locale.KOREAN);
        try {
            StatsRequestExceptionHandler handler = new StatsRequestExceptionHandler(messageSource);

            ResponseEntity<Object> response =
                    handler.handleTypeMismatch(mismatch("202608", YearMonth.class));

            ApiResult<?> body = (ApiResult<?>) response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getMessage()).isEqualTo("from 형식 오류 (기대: yyyy-MM)");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    @DisplayName("필수 파라미터 누락: 400 + 100102")
    void missingParameter() {
        StatsRequestExceptionHandler handler = new StatsRequestExceptionHandler(null);

        ResponseEntity<Object> response =
                handler.handleMissing(
                        new MissingServletRequestParameterException("to", "LocalDate"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResult<?> body = (ApiResult<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("100102");
        assertThat(body.getMessage()).isEqualTo("error.stats.param.missing");
    }

    @Test
    @DisplayName("기대 형식 설명: 날짜·월은 패턴, enum 은 허용값, 그 외는 타입 이름, null 은 '-'")
    void describeExpectedType() {
        assertThat(StatsRequestExceptionHandler.describeExpectedType(LocalDate.class))
                .isEqualTo("yyyy-MM-dd");
        assertThat(StatsRequestExceptionHandler.describeExpectedType(YearMonth.class))
                .isEqualTo("yyyy-MM");
        assertThat(StatsRequestExceptionHandler.describeExpectedType(RetentionSource.class))
                .contains("REQUEST")
                .contains("ATTENDANCE");
        assertThat(StatsRequestExceptionHandler.describeExpectedType(Integer.class))
                .isEqualTo("Integer");
        assertThat(StatsRequestExceptionHandler.describeExpectedType(null)).isEqualTo("-");
    }
}
