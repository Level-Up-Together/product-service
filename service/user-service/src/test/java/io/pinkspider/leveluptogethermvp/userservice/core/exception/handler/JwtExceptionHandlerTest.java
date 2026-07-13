package io.pinkspider.leveluptogethermvp.userservice.core.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.core.api.UserApiStatus;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.custom.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class JwtExceptionHandlerTest {

    private final JwtExceptionHandler handler = new JwtExceptionHandler();

    @Test
    @DisplayName("JwtException은 401과 원본 에러 코드로 응답한다")
    void handleJwtException_returns401WithCode() {
        // given
        JwtException exception =
                new JwtException(
                        UserApiStatus.NOT_VALID_REFRESH_TOKEN.getResultCode(),
                        UserApiStatus.NOT_VALID_REFRESH_TOKEN.getResultMessage());

        // when
        ResponseEntity<Object> response = handler.handleJwtException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ApiResult<?> body = (ApiResult<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("010102");
    }

    @Test
    @DisplayName("절대 상한 초과(010106)도 401로 응답한다")
    void handleJwtException_maxLifetime_returns401() {
        // given
        JwtException exception =
                new JwtException(
                        UserApiStatus.TOKEN_EXCEEDED_MAXIMUM_LIFETIME.getResultCode(),
                        UserApiStatus.TOKEN_EXCEEDED_MAXIMUM_LIFETIME.getResultMessage());

        // when
        ResponseEntity<Object> response = handler.handleJwtException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ApiResult<?> body = (ApiResult<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("010106");
    }
}
