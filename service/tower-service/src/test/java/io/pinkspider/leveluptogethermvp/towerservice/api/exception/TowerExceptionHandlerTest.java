package io.pinkspider.leveluptogethermvp.towerservice.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TowerExceptionHandlerTest {

    @Test
    @DisplayName("없음은 404, 규칙 위반은 400 — MessageSource 가 없으면 메시지 키 그대로")
    void statusByException() {
        TowerExceptionHandler handler = new TowerExceptionHandler(null);

        ResponseEntity<Object> notFound = handler.handle(TowerFloorException.notFound());
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((ApiResult<?>) notFound.getBody()).getCode()).isEqualTo("150101");

        ResponseEntity<Object> dup = handler.handle(TowerFloorException.duplicateNumber());
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResult<?> body = (ApiResult<?>) dup.getBody();
        assertThat(body.getCode()).isEqualTo("150102");
        assertThat(body.getMessage()).isEqualTo("error.tower.floor.duplicate_number");
    }

    @Test
    @DisplayName("MessageSource 가 있으면 현지화 메시지")
    void localized() {
        StaticMessageSource ms = new StaticMessageSource();
        ms.addMessage("error.tower.floor.entry_point_outside_mask", Locale.KOREAN, "마스크 밖");
        LocaleContextHolder.setLocale(Locale.KOREAN);
        try {
            TowerExceptionHandler handler = new TowerExceptionHandler(ms);
            ResponseEntity<Object> res =
                    handler.handle(TowerFloorException.entryPointOutsideMask());
            assertThat(((ApiResult<?>) res.getBody()).getMessage()).isEqualTo("마스크 밖");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    @DisplayName("LUT-738 문구 인자와 세부 값: 10초 안 재이동은 429, 남은 초를 문구와 value.retry_after_seconds 로")
    void detailAndArgs() throws Exception {
        StaticMessageSource ms = new StaticMessageSource();
        ms.addMessage("error.tower.room.move_too_soon", Locale.KOREAN, "{0}초 뒤에 다시");
        LocaleContextHolder.setLocale(Locale.KOREAN);
        try {
            TowerExceptionHandler handler = new TowerExceptionHandler(ms);
            ResponseEntity<Object> res =
                    handler.handle(
                            io.pinkspider.leveluptogethermvp.towerservice.application.exception
                                    .TowerEntryException.roomMoveTooSoon(7));
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            ApiResult<?> body = (ApiResult<?>) res.getBody();
            assertThat(body.getCode()).isEqualTo("150208");
            assertThat(body.getMessage()).isEqualTo("7초 뒤에 다시");
            assertThat(body.getValue()).isEqualTo(java.util.Map.of("retry_after_seconds", 7));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
        TowerExceptionHandler bare = new TowerExceptionHandler(null);
        assertThat(
                        bare.handle(
                                        io.pinkspider.leveluptogethermvp.towerservice.application
                                                .exception.TowerEntryException.roomFull())
                                .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(
                        ((ApiResult<?>)
                                        bare.handle(
                                                        io.pinkspider.leveluptogethermvp
                                                                .towerservice.application.exception
                                                                .TowerEntryException.roomNotFound())
                                                .getBody())
                                .getValue())
                .isNull();
    }
}
