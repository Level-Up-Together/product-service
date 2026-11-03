package io.pinkspider.leveluptogethermvp.missionservice.api.exception;

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

@DisplayName("MissionIdempotencyExceptionHandler (LUT-577 400/409 응답)")
class MissionIdempotencyExceptionHandlerTest {

    @Test
    @DisplayName("UUID 형식 오류 → 400 + 050401 + 현지화 메시지")
    void invalidKey_400_localized() {
        MissionIdempotencyExceptionHandler handler = new MissionIdempotencyExceptionHandler();
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(
                        eq("error.mission.idempotency_key.invalid"),
                        isNull(),
                        eq("error.mission.idempotency_key.invalid"),
                        any()))
                .thenReturn("Idempotency-Key 헤더는 UUID 형식이어야 합니다.");
        ReflectionTestUtils.setField(handler, "messageSource", messageSource);

        ResponseEntity<Object> res = handler.handle(MissionIdempotencyException.invalidKey());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResult<?> body = (ApiResult<?>) res.getBody();
        assertThat(body.getCode()).isEqualTo("050401");
        assertThat(body.getMessage()).isEqualTo("Idempotency-Key 헤더는 UUID 형식이어야 합니다.");
    }

    @Test
    @DisplayName("처리 중 → 409 + 050402. MessageSource 가 없으면 키를 그대로 돌려준다")
    void inProgress_409_withoutMessageSource() {
        MissionIdempotencyExceptionHandler handler = new MissionIdempotencyExceptionHandler();

        ResponseEntity<Object> res = handler.handle(MissionIdempotencyException.inProgress());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiResult<?> body = (ApiResult<?>) res.getBody();
        assertThat(body.getCode()).isEqualTo("050402");
        assertThat(body.getMessage()).isEqualTo("error.mission.idempotency_key.in_progress");
    }
}
