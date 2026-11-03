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
}
