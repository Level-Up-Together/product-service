package io.pinkspider.leveluptogethermvp.bffservice.api.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.api.ApiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("BffSeasonExceptionHandler (LUT-804 시즌 없음 404)")
class BffSeasonExceptionHandlerTest {

    @Test
    @DisplayName("진행 중인 시즌 없음 → 404 + NO_ACTIVE_SEASON + 현지화 메시지")
    void noActive_404_localized() {
        BffSeasonExceptionHandler handler = new BffSeasonExceptionHandler();
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(
                        eq("error.season.no_active"),
                        isNull(),
                        eq("error.season.no_active"),
                        any()))
                .thenReturn("현재 활성화된 시즌이 없습니다.");
        ReflectionTestUtils.setField(handler, "messageSource", messageSource);

        ResponseEntity<Object> res = handler.handle(SeasonNotFoundException.noActive());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiResult<?> body = (ApiResult<?>) res.getBody();
        assertThat(body.getCode()).isEqualTo("NO_ACTIVE_SEASON");
        assertThat(body.getMessage()).isEqualTo("현재 활성화된 시즌이 없습니다.");
    }

    @Test
    @DisplayName("없는 시즌 ID → 404 + SEASON_NOT_FOUND. MessageSource 가 없으면 키를 그대로 돌려준다")
    void notFound_404_withoutMessageSource() {
        BffSeasonExceptionHandler handler = new BffSeasonExceptionHandler();

        ResponseEntity<Object> res = handler.handle(SeasonNotFoundException.notFound());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiResult<?> body = (ApiResult<?>) res.getBody();
        assertThat(body.getCode()).isEqualTo("SEASON_NOT_FOUND");
        assertThat(body.getMessage()).isEqualTo("error.season.not_found");
    }
}
